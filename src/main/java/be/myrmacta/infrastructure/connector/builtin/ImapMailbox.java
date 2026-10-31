package be.myrmacta.infrastructure.connector.builtin;

import be.myrmacta.domain.connector.model.InboundAttachment;
import java.time.Instant;
import java.util.List;

/**
 * TODO §87 (D3c) — accès à une boîte e-mail : lit les messages d'un dossier, les confie à un gestionnaire, puis range chacun dans le
 * dossier « traités » ou « refusés » (jamais supprimé : la boîte reste la preuve de ce qui est arrivé).
 */
interface ImapMailbox {

    /**
     * @throws RuntimeException connexion, authentification ou protocole en échec : rien n'est rangé, la prochaine interrogation
     *     reprendra les mêmes messages
     */
    void process(Settings settings, int maxMessages, Limits limits, Handler handler);

    record Settings(
            String host,
            int port,
            Security security,
            String username,
            /** Mot de passe, ou jeton d'accès OAuth2 (XOAUTH2) quand {@code oauth}. */
            String secret,
            boolean oauth,
            String folder,
            String processedFolder,
            String rejectedFolder,
            int timeoutMillis) {

        @Override
        public String toString() {
            return "Settings[host=" + host + ", port=" + port + ", folder=" + folder + "]";
        }
    }

    enum Security {
        SSL,
        STARTTLS,
        /** Sans chiffrement : réservé aux tests (serveur en mémoire) ; jamais produit par le connecteur, qui refuse toute autre valeur. */
        PLAIN_FOR_TESTS
    }

    record Limits(long maxMessageBytes, int maxBodyChars, long maxAttachmentBytes, int maxAttachments) {}

    record Mail(
            String messageId,
            String fromAddress,
            String fromDisplay,
            String subject,
            Instant sentAt,
            String body,
            boolean automatic,
            /** Premier en-tête {@code Authentication-Results} du message (celui que notre serveur de réception ajoute en tête), brut. */
            String authenticationResults,
            List<InboundAttachment> attachments,
            int attachmentsSkipped,
            /** En-têtes de fil (TODO §112, D-MAILROOM-THREAD), {@link MailThread#NONE} hors fil. */
            MailThread thread) {

        public Mail {
            thread = thread == null ? MailThread.NONE : thread;
        }

        public Mail(String messageId, String fromAddress, String fromDisplay, String subject, Instant sentAt, String body, boolean automatic,
                String authenticationResults, List<InboundAttachment> attachments, int attachmentsSkipped) {
            this(messageId, fromAddress, fromDisplay, subject, sentAt, body, automatic, authenticationResults, attachments,
                    attachmentsSkipped, MailThread.NONE);
        }
    }

    enum Outcome {
        PROCESSED,
        REJECTED
    }

    interface Handler {
        /** Ne lève jamais pour un message invalide : renvoie {@link Outcome#REJECTED}. */
        Outcome handle(Mail mail);

        /** Message trop gros pour être analysé : il est rangé sans lecture. */
        void oversize(long bytes);

        /** Message illisible (encodage inconnu, MIME trop imbriqué…) : rangé dans « refusés » ; ne bloque jamais la boîte. */
        void unreadable(String reason);
    }
}
