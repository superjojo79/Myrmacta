package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.InboundAttachment;
import jakarta.mail.Address;
import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.FolderClosedException;
import jakarta.mail.StoreClosedException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.search.FlagTerm;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * TODO §87 (D3c) — {@link ImapMailbox} par Jakarta Mail (Angus, déjà présent pour l'envoi SMTP). TLS vérifié (nom du serveur compris),
 * jamais de certificat accepté à l'aveugle. Un message est rangé <em>après</em> que le gestionnaire l'a pris en charge ; une
 * panne de transport laisse tout en place pour la prochaine interrogation.
 */
@Component
class AngusImapMailbox implements ImapMailbox {

    /** Mot-clé IMAP posé sur un message déjà remis au runtime (idempotence d'un rangement qui échoue). */
    static final Flags DELIVERED = new Flags("MyrmactaDelivered");
    /** Ancien mot-clé, encore reconnu : un message déjà remis n'est pas retraité. */
    static final Flags LEGACY_DELIVERED = new Flags("FlowForgeDelivered"); // flowforge-legacy

    static boolean alreadyDelivered(Flags flags) {
        return flags != null && (flags.contains(DELIVERED) || flags.contains(LEGACY_DELIVERED));
    }
    static final int MAX_MIME_DEPTH = 10;
    static final int MAX_MIME_PARTS = 200;

    private static final Pattern TAGS = Pattern.compile("<[^>]{0,2000}>");
    private static final Pattern STYLE_SCRIPT = Pattern.compile("(?is)<(style|script)[^>]*>.*?</\\1>");
    private static final Pattern BLANKS = Pattern.compile("[ \\t\\x0B\\f\\r]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    @Override
    public void process(Settings s, int maxMessages, Limits limits, Handler handler) {
        Session session = Session.getInstance(properties(s));
        try (Store store = session.getStore(s.security() == Security.SSL ? "imaps" : "imap")) {
            store.connect(s.host(), s.port(), s.username(), s.secret());
            Folder inbox = store.getFolder(s.folder());
            inbox.open(Folder.READ_WRITE);
            try {
                Folder processed = target(store, s.processedFolder());
                Folder rejected = target(store, s.rejectedFolder());
                // Les messages marqués supprimés (tour interrompu, autre client) ne comptent pas dans la limite du lot : ils ne
                // doivent jamais empêcher la lecture des nouveaux.
                Message[] live = inbox.search(new FlagTerm(new Flags(Flags.Flag.DELETED), false));
                int count = Math.min(live.length, maxMessages);
                boolean canFlag = inbox.getPermanentFlags().contains(Flags.Flag.USER);
                for (int index = 0; index < count; index++) {
                    MimeMessage message = (MimeMessage) live[index];
                    Outcome outcome;
                    if (canFlag && alreadyDelivered(message.getFlags())) {
                        // Déjà remis lors d'un tour dont le rangement a échoué : on réessaie seulement le rangement (jamais de 2e dossier).
                        outcome = Outcome.PROCESSED;
                    } else try {
                        long size = message.getSize();
                        if (size > limits.maxMessageBytes()) {
                            handler.oversize(size);
                            outcome = Outcome.REJECTED;
                        } else {
                            outcome = handler.handle(parse(message, limits));
                        }
                    } catch (FolderClosedException | StoreClosedException transport) {
                        throw transport;
                    } catch (MessagingException ex) {
                        if (ex.getCause() instanceof java.net.SocketException || ex.getCause() instanceof java.net.SocketTimeoutException) {
                            throw ex; // panne de transport : rien n'est rangé, la prochaine interrogation reprend
                        }
                        handler.unreadable(ex.getClass().getSimpleName());
                        outcome = Outcome.REJECTED;
                    } catch (IOException | RuntimeException | StackOverflowError unreadable) {
                        // Un message empoisonné (encodage inconnu, MIME imbriqué à l'excès) est écarté : il ne bloque jamais la boîte.
                        handler.unreadable(unreadable.getClass().getSimpleName());
                        outcome = Outcome.REJECTED;
                    }
                    if (outcome == Outcome.PROCESSED && canFlag) {
                        message.setFlags(DELIVERED, true); // posé AVANT le rangement : si celui-ci échoue, le message ne sera pas remis deux fois
                    }
                    relocate(inbox, message, outcome == Outcome.PROCESSED ? processed : rejected);
                    message.setFlag(Flags.Flag.DELETED, true);
                    // Retiré tout de suite : un arrêt en milieu de lot ne laisse jamais une copie rangée ET l'original en place.
                    inbox.expunge();
                }
            } finally {
                if (inbox.isOpen()) {
                    inbox.close(false);
                }
            }
        } catch (MessagingException ex) {
            // Message neutre : jamais le mot de passe ni le jeton (certains serveurs les reprennent dans leurs refus).
            throw new IllegalStateException("mailbox access failed (" + ex.getClass().getSimpleName() + ")", ex);
        }
    }

    /** Copie vers le dossier cible (surchargeable en test pour simuler un rangement qui échoue après la remise). */
    void relocate(Folder from, Message message, Folder to) throws MessagingException {
        from.copyMessages(new Message[] {message}, to);
    }

    private static Folder target(Store store, String name) throws MessagingException {
        Folder folder = store.getFolder(name);
        if (!folder.exists()) {
            folder.create(Folder.HOLDS_MESSAGES);
        }
        return folder;
    }

    private static Properties properties(Settings s) {
        Properties p = new Properties();
        String prefix = s.security() == Security.SSL ? "mail.imaps." : "mail.imap.";
        p.put(prefix + "host", s.host());
        p.put(prefix + "port", String.valueOf(s.port()));
        p.put(prefix + "connectiontimeout", String.valueOf(s.timeoutMillis()));
        p.put(prefix + "timeout", String.valueOf(s.timeoutMillis()));
        p.put(prefix + "writetimeout", String.valueOf(s.timeoutMillis()));
        p.put(prefix + "ssl.checkserveridentity", "true");
        if (s.security() == Security.SSL) {
            p.put("mail.imaps.ssl.enable", "true");
        } else if (s.security() == Security.PLAIN_FOR_TESTS) {
            p.put("mail.imap.ssl.checkserveridentity", "false");
        } else {
            p.put("mail.imap.starttls.enable", "true");
            p.put("mail.imap.starttls.required", "true");
            p.put("mail.imap.ssl.checkserveridentity", "true");
        }
        if (s.oauth()) {
            p.put(prefix + "auth.mechanisms", "XOAUTH2");
            p.put(prefix + "auth.xoauth2.disable", "false");
            p.put(prefix + "sasl.enable", "true");
        } else {
            p.put(prefix + "auth.mechanisms", "PLAIN LOGIN");
        }
        return p;
    }

    private Mail parse(MimeMessage message, Limits limits) throws MessagingException, IOException {
        String messageId = header(message, "Message-ID");
        Address[] from = message.getFrom();
        String fromAddress = "";
        String fromDisplay = "";
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress ia) {
            fromAddress = ia.getAddress() == null ? "" : ia.getAddress();
            fromDisplay = ia.getPersonal() == null ? "" : ia.getPersonal();
        }
        String autoSubmitted = header(message, "Auto-Submitted").toLowerCase(Locale.ROOT);
        String precedence = header(message, "Precedence").toLowerCase(Locale.ROOT);
        // Réponses automatiques, listes, notifications : jamais un dossier (évite les boucles avec nos propres envois).
        boolean automatic = (!autoSubmitted.isBlank() && !"no".equals(autoSubmitted))
                || precedence.contains("bulk") || precedence.contains("junk") || precedence.contains("list")
                || !header(message, "X-Auto-Response-Suppress").isBlank();
        Content content = new Content(limits);
        walk(message, content, 0);
        String authenticationResults = header(message, "Authentication-Results");
        Instant sent = message.getSentDate() == null ? null : message.getSentDate().toInstant();
        String body = content.text.length() > 0 ? content.text.toString() : content.html.length() > 0 ? htmlToText(content.html.toString()) : "";
        if (body.length() > limits.maxBodyChars()) {
            body = body.substring(0, limits.maxBodyChars());
        }
        String subject = message.getSubject() == null ? "" : message.getSubject();
        MailThread thread = MailThread.of(header(message, "In-Reply-To"), joinedHeader(message, "References"), "");
        return new Mail(messageId, fromAddress, fromDisplay, subject, sent, body.strip(), automatic, authenticationResults,
                List.copyOf(content.attachments), content.skipped, thread);
    }

    private static String header(MimeMessage message, String name) throws MessagingException {
        String[] values = message.getHeader(name);
        return values == null || values.length == 0 || values[0] == null ? "" : values[0].strip();
    }

    /** Toutes les occurrences d'un en-tête, jointes : un {@code References} peut être réparti sur plusieurs lignes. */
    private static String joinedHeader(MimeMessage message, String name) throws MessagingException {
        String joined = message.getHeader(name, " ");
        return joined == null ? "" : joined;
    }

    private static final class Content {
        final Limits limits;
        final StringBuilder text = new StringBuilder();
        final StringBuilder html = new StringBuilder();
        final List<InboundAttachment> attachments = new ArrayList<>();
        int skipped;
        int parts;

        Content(Limits limits) {
            this.limits = limits;
        }
    }

    private void walk(Part part, Content out, int depth) throws MessagingException, IOException {
        if (depth > MAX_MIME_DEPTH || ++out.parts > MAX_MIME_PARTS) {
            throw new MessagingException("message too complex");
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart child = multipart.getBodyPart(i);
                walk(child, out, depth + 1);
            }
            return;
        }
        String disposition = part.getDisposition();
        String filename = part.getFileName();
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(disposition) || (filename != null && !filename.isBlank());
        if (!attachment && part.isMimeType("text/plain")) {
            if (out.text.length() < out.limits.maxBodyChars()) {
                out.text.append(readText(part, out.limits.maxBodyChars()));
            }
            return;
        }
        if (!attachment && part.isMimeType("text/html")) {
            if (out.html.length() < out.limits.maxBodyChars() * 4L) {
                out.html.append(readText(part, out.limits.maxBodyChars() * 4));
            }
            return;
        }
        if (part.isMimeType("message/rfc822") || !attachment) {
            return; // message transféré, texte inline autre : ignoré
        }
        if (out.attachments.size() >= out.limits.maxAttachments()) {
            out.skipped++;
            return;
        }
        long declared = part.getSize();
        if (declared > out.limits.maxAttachmentBytes()) {
            out.skipped++;
            return;
        }
        try (InputStream in = part.getInputStream()) {
            // Lecture bornée : une taille annoncée fausse ne force jamais une lecture sans limite.
            byte[] bytes = in.readNBytes((int) Math.min(out.limits.maxAttachmentBytes() + 1, Integer.MAX_VALUE - 8));
            if (bytes.length > out.limits.maxAttachmentBytes()) {
                out.skipped++;
                return;
            }
            String type = baseType(part.getContentType());
            out.attachments.add(new InboundAttachment(safeFilename(filename), type, bytes));
        }
    }

    private static String readText(Part part, int maxChars) throws MessagingException, IOException {
        Object content = part.getContent();
        String text = content instanceof String s ? s : content instanceof InputStream in
                ? new String(in.readNBytes(maxChars * 4), StandardCharsets.UTF_8) : "";
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }

    static String htmlToText(String html) {
        String text = STYLE_SCRIPT.matcher(html).replaceAll(" ");
        text = text.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</tr>|</li>", "\n");
        text = TAGS.matcher(text).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
        text = BLANKS.matcher(text).replaceAll(" ");
        text = text.replaceAll(" ?\\n ?", "\n");
        return BLANK_LINES.matcher(text).replaceAll("\n\n").strip();
    }

    private static String baseType(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        int semicolon = contentType.indexOf(';');
        String base = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).strip().toLowerCase(Locale.ROOT);
        return base.isBlank() ? "application/octet-stream" : base;
    }

    /** Nom de fichier assaini : ni chemin, ni caractère de contrôle (il devient une métadonnée affichée). */
    static String safeFilename(String raw) {
        if (raw == null) {
            return "";
        }
        String name = raw.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
