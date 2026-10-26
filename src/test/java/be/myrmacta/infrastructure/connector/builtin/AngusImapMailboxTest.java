package be.myrmacta.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.InboundAttachment;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** TODO §87 (D3c) — accès IMAP réel (serveur GreenMail en mémoire) : analyse MIME, bornes, rangement, pannes. */
class AngusImapMailboxTest {

    @RegisterExtension
    static final GreenMailExtension GREEN = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static final ImapMailbox.Limits LIMITS = new ImapMailbox.Limits(1_000_000, 500, 10_000, 2);

    private GreenMailUser user;
    private final AngusImapMailbox mailbox = new AngusImapMailbox();

    @BeforeEach
    void setUp() {
        user = GREEN.setUser("claims@example.com", "claims", "s3cret");
    }

    private ImapMailbox.Settings settings(String password) {
        return new ImapMailbox.Settings("127.0.0.1", ServerSetupTest.IMAP.getPort(), ImapMailbox.Security.PLAIN_FOR_TESTS, "claims",
                password, false, "INBOX", "Myrmacta-processed", "Myrmacta-rejected", 10_000);
    }

    private MimeMessage newMessage() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("client@example.org", "Un Client"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("claims@example.com"));
        return message;
    }

    private void deliver(MimeMessage message) throws Exception {
        user.deliver(message);
    }

    private static class Collector implements ImapMailbox.Handler {
        final List<ImapMailbox.Mail> mails = new ArrayList<>();
        final List<Long> oversize = new ArrayList<>();
        ImapMailbox.Outcome outcome = ImapMailbox.Outcome.PROCESSED;

        @Override
        public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
            mails.add(mail);
            return mail.automatic() ? ImapMailbox.Outcome.REJECTED : outcome;
        }

        final List<String> unreadable = new ArrayList<>();

        @Override
        public void oversize(long bytes) {
            oversize.add(bytes);
        }

        @Override
        public void unreadable(String reason) {
            unreadable.add(reason);
        }
    }

    private int count(String folder) throws Exception {
        var store = Session.getInstance(new Properties()).getStore("imap");
        store.connect("127.0.0.1", ServerSetupTest.IMAP.getPort(), "claims", "s3cret");
        try {
            var f = store.getFolder(folder);
            if (!f.exists()) {
                return -1;
            }
            f.open(jakarta.mail.Folder.READ_ONLY);
            int n = f.getMessageCount();
            f.close(false);
            return n;
        } finally {
            store.close();
        }
    }

    @Test
    void aPlainTextMessageIsParsedThenMovedToTheProcessedFolder_neverDeleted() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("RE: [DOS-2026-0042] Panne");
        m.setText("Bonjour, voici ma demande.", "UTF-8");
        m.saveChanges();
        deliver(m);
        var collector = new Collector();

        mailbox.process(settings("s3cret"), 10, LIMITS, collector);

        assertThat(collector.mails).hasSize(1);
        var mail = collector.mails.getFirst();
        assertThat(mail.subject()).isEqualTo("RE: [DOS-2026-0042] Panne");
        assertThat(mail.fromAddress()).isEqualTo("client@example.org");
        assertThat(mail.fromDisplay()).isEqualTo("Un Client");
        assertThat(mail.body()).isEqualTo("Bonjour, voici ma demande.");
        assertThat(mail.automatic()).isFalse();
        assertThat(count("INBOX")).isZero();
        assertThat(count("Myrmacta-processed")).isEqualTo(1);
        assertThat(count("Myrmacta-rejected")).isZero();
    }

    @Test
    void anHtmlOnlyMessageBecomesText_andScriptsAndStylesAreDropped() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("html");
        m.setContent("<html><style>p{color:red}</style><body><p>Bonjour <b>vous</b></p><script>alert(1)</script><p>Merci&nbsp;!</p></body></html>",
                "text/html; charset=UTF-8");
        m.saveChanges();
        deliver(m);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails.getFirst().body()).contains("Bonjour vous").contains("Merci").doesNotContain("alert").doesNotContain("color:red")
                .doesNotContain("<");
    }

    @Test
    void attachmentsAreKeptWithinTheBounds_theRestAreCounted() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("pièces");
        MimeMultipart multipart = new MimeMultipart();
        MimeBodyPart text = new MimeBodyPart();
        text.setText("voir pièces", "UTF-8");
        multipart.addBodyPart(text);
        for (String name : List.of("a.pdf", "b.pdf", "c.pdf")) {
            MimeBodyPart part = new MimeBodyPart();
            part.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource("%PDF-1.4 " + name, "application/pdf")));
            part.setFileName(name);
            multipart.addBodyPart(part);
        }
        MimeBodyPart big = new MimeBodyPart();
        big.setDataHandler(new jakarta.activation.DataHandler(new jakarta.mail.util.ByteArrayDataSource(new byte[20_000], "application/pdf")));
        big.setFileName("../../etc/gros.pdf");
        multipart.addBodyPart(big);
        m.setContent(multipart);
        m.saveChanges();
        deliver(m);
        var collector = new Collector();

        mailbox.process(settings("s3cret"), 10, LIMITS, collector);

        var mail = collector.mails.getFirst();
        assertThat(mail.body()).isEqualTo("voir pièces");
        assertThat(mail.attachments()).extracting(InboundAttachment::filename).containsExactly("a.pdf", "b.pdf");
        assertThat(mail.attachments().getFirst().contentType()).isEqualTo("application/pdf");
        assertThat(mail.attachmentsSkipped()).isEqualTo(2); // c.pdf (au-delà du nombre) et le fichier trop gros
    }

    @Test
    void automaticMessagesAreFlaggedAndGoToTheRejectedFolder() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("Absence");
        m.setHeader("Auto-Submitted", "auto-replied");
        m.setText("je suis absent", "UTF-8");
        m.saveChanges();
        deliver(m);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails.getFirst().automatic()).isTrue();
        assertThat(count("Myrmacta-rejected")).isEqualTo(1);
        assertThat(count("Myrmacta-processed")).isLessThanOrEqualTo(0);
    }

    @Test
    void anOversizedMessageIsMovedAsideWithoutBeingParsed() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("énorme");
        m.setText("x".repeat(5_000), "UTF-8");
        m.saveChanges();
        deliver(m);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 10, new ImapMailbox.Limits(1_000, 500, 10_000, 2), collector);
        assertThat(collector.mails).isEmpty();
        assertThat(collector.oversize).hasSize(1);
        assertThat(count("Myrmacta-rejected")).isEqualTo(1);
    }

    @Test
    void onlyTheFirstMessagesUpToTheLimitAreTaken() throws Exception {
        for (int i = 0; i < 3; i++) {
            MimeMessage m = newMessage();
            m.setSubject("m" + i);
            m.setText("x", "UTF-8");
            m.saveChanges();
            deliver(m);
        }
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 2, LIMITS, collector);
        assertThat(collector.mails).hasSize(2);
        assertThat(count("INBOX")).isEqualTo(1);
    }

    @Test
    void aWrongPasswordFailsWithANeutralMessageAndMovesNothing() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("x");
        m.setText("x", "UTF-8");
        m.saveChanges();
        deliver(m);
        assertThatThrownBy(() -> mailbox.process(settings("WRONG-PASSWORD"), 10, LIMITS, new Collector()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("WRONG-PASSWORD")
                .hasMessageContaining("mailbox access failed");
        assertThat(count("INBOX")).isEqualTo(1);
    }

    @Test
    void theSettingsNeverPrintTheirSecret() {
        assertThat(settings("s3cret").toString()).doesNotContain("s3cret");
    }

    @Test
    void htmlToTextHandlesBreaksAndEntities() {
        assertThat(AngusImapMailbox.htmlToText("<p>a</p><p>b&amp;c</p>")).isEqualTo("a\nb&c");
        assertThat(AngusImapMailbox.safeFilename("C:\\temp\\../x\u0007y.pdf")).isEqualTo("xy.pdf");
        assertThat(GreenMailUtil.getBody(newMessageQuiet())).isNotNull();
    }

    // ---- revue adversariale D3c/D3d ----

    private MimeMessage withContentType(String subject, String contentType, String text) throws Exception {
        MimeMessage m = newMessage();
        m.setSubject(subject);
        m.setText(text, "UTF-8");
        m.setHeader("Content-Type", contentType);
        m.saveChanges();
        m.setHeader("Content-Type", contentType); // saveChanges() normalise : on force l'encodage inconnu
        return m;
    }

    @Test
    void aPoisonedMessageIsSetAsideNeverBlockingTheMailbox_andTheOthersAreTakenExactlyOnce() throws Exception {
        MimeMessage first = newMessage();
        first.setSubject("m1");
        first.setText("un", "UTF-8");
        first.saveChanges();
        deliver(first);
        deliver(withContentType("poison", "text/plain; charset=x-bogus-charset", "empoisonné"));
        MimeMessage third = newMessage();
        third.setSubject("m3");
        third.setText("trois", "UTF-8");
        third.saveChanges();
        deliver(third);
        var collector = new Collector();

        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        mailbox.process(settings("s3cret"), 10, LIMITS, collector); // 2e interrogation : rien ne rejoue

        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("m1", "m3");
        assertThat(collector.unreadable).hasSize(1);
        assertThat(count("INBOX")).isZero();
        assertThat(count("Myrmacta-processed")).isEqualTo(2);
        assertThat(count("Myrmacta-rejected")).isEqualTo(1);
    }

    @Test
    void aDeeplyNestedMimeBombIsRejectedNotParsedToTheEnd() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("bombe");
        MimeMultipart inner = new MimeMultipart();
        MimeBodyPart leaf = new MimeBodyPart();
        leaf.setText("feuille", "UTF-8");
        inner.addBodyPart(leaf);
        for (int i = 0; i < 15; i++) {
            MimeBodyPart wrapper = new MimeBodyPart();
            wrapper.setContent(inner, "multipart/mixed");
            MimeMultipart outer = new MimeMultipart();
            outer.addBodyPart(wrapper);
            inner = outer;
        }
        m.setContent(inner);
        m.saveChanges();
        deliver(m);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails).isEmpty();
        assertThat(collector.unreadable).hasSize(1);
        assertThat(count("Myrmacta-rejected")).isEqualTo(1);
    }

    @Test
    void aHandlerThatThrowsSetsTheMessageAsideInsteadOfLosingTheBatch() throws Exception {
        for (String subject : List.of("a", "b")) {
            MimeMessage m = newMessage();
            m.setSubject(subject);
            m.setText("x", "UTF-8");
            m.saveChanges();
            deliver(m);
        }
        var collector = new Collector() {
            @Override
            public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
                if (mail.subject().equals("a")) {
                    throw new IllegalStateException("boom");
                }
                return super.handle(mail);
            }
        };
        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("b");
        assertThat(count("INBOX")).isZero();
        assertThat(count("Myrmacta-rejected")).isEqualTo(1);
    }

    @Test
    void theFirstAuthenticationResultsHeaderIsExposedRawAndAForgedOneBelowIsNotTheOneRead() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("forgé");
        m.setText("x", "UTF-8");
        m.saveChanges();
        m.addHeader("Authentication-Results", "mx.example.com; dkim=fail; spf=fail");
        m.addHeader("Authentication-Results", "evil.example; dkim=pass; spf=pass");
        deliver(m);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails.getFirst().authenticationResults()).startsWith("mx.example.com; dkim=fail");
    }

    @Test
    void messagesFlaggedDeletedAtTheHeadOfTheBoxNeverBlockTheNewOnes() throws Exception {
        for (int i = 0; i < 3; i++) {
            MimeMessage stale = newMessage();
            stale.setSubject("stale" + i);
            stale.setText("x", "UTF-8");
            stale.setFlag(jakarta.mail.Flags.Flag.DELETED, true);
            stale.saveChanges();
            deliver(stale);
        }
        MimeMessage fresh = newMessage();
        fresh.setSubject("fresh");
        fresh.setText("x", "UTF-8");
        fresh.saveChanges();
        deliver(fresh);
        var collector = new Collector();
        mailbox.process(settings("s3cret"), 3, LIMITS, collector); // limite de 3 : les 3 supprimés ne la consomment pas
        assertThat(collector.mails).extracting(ImapMailbox.Mail::subject).containsExactly("fresh");
    }

    @Test
    void aMessageAlreadyDeliveredWhoseMoveFailedIsNeverDeliveredTwice() throws Exception {
        MimeMessage m = newMessage();
        m.setSubject("une seule fois");
        m.setText("x", "UTF-8");
        m.saveChanges();
        deliver(m);
        var collector = new Collector();
        // le rangement échoue trois fois APRÈS la remise (quota, droits…)
        var failing = new AngusImapMailbox() {
            int attempts;

            @Override
            void relocate(jakarta.mail.Folder from, jakarta.mail.Message message, jakarta.mail.Folder to) throws jakarta.mail.MessagingException {
                if (attempts++ < 3) {
                    throw new jakarta.mail.MessagingException("quota exceeded");
                }
                super.relocate(from, message, to);
            }
        };
        for (int i = 0; i < 3; i++) {
            try {
                failing.process(settings("s3cret"), 10, LIMITS, collector);
            } catch (RuntimeException expected) {
                // le rangement échoue : la prochaine interrogation ne doit pas remettre le message
            }
        }
        assertThat(collector.mails).as("remis une seule fois malgré trois interrogations").hasSize(1);
        // une fois le dossier corrigé, le message est rangé sans nouvelle remise
        failing.process(settings("s3cret"), 10, LIMITS, collector);
        assertThat(collector.mails).hasSize(1);
        assertThat(count("Myrmacta-processed")).isEqualTo(1);
        assertThat(count("INBOX")).isZero();
    }

    @Test
    void bidirectionalControlsAreStrippedFromAFileName() {
        assertThat(AngusImapMailbox.safeFilename("invoice\u202Efdp.exe")).isEqualTo("invoicefdp.exe");
    }

    private MimeMessage newMessageQuiet() {
        try {
            MimeMessage m = newMessage();
            m.setText("ok");
            return m;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
