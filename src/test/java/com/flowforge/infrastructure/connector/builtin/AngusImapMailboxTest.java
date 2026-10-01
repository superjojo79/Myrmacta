package com.flowforge.infrastructure.connector.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.InboundAttachment;
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
                password, false, "INBOX", "FlowForge-processed", "FlowForge-rejected", 10_000);
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

    private static final class Collector implements ImapMailbox.Handler {
        final List<ImapMailbox.Mail> mails = new ArrayList<>();
        final List<Long> oversize = new ArrayList<>();
        ImapMailbox.Outcome outcome = ImapMailbox.Outcome.PROCESSED;

        @Override
        public ImapMailbox.Outcome handle(ImapMailbox.Mail mail) {
            mails.add(mail);
            return mail.automatic() ? ImapMailbox.Outcome.REJECTED : outcome;
        }

        @Override
        public void oversize(long bytes) {
            oversize.add(bytes);
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
        assertThat(count("FlowForge-processed")).isEqualTo(1);
        assertThat(count("FlowForge-rejected")).isZero();
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
        assertThat(count("FlowForge-rejected")).isEqualTo(1);
        assertThat(count("FlowForge-processed")).isLessThanOrEqualTo(0);
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
        assertThat(count("FlowForge-rejected")).isEqualTo(1);
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
