package be.myrmacta.domain.notification.port.out;

import java.util.Objects;

public interface EmailPort {

    /** E-mail texte sans pièce jointe. */
    default void send(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    /**
     * Issue #183 — e-mail texte avec une pièce jointe ({@code null} : aucune). Le contenu a été relu dans le stockage du
     * tenant et contrôlé (document produit du dossier, PDF, plafond) avant d'arriver ici.
     */
    void send(String to, String subject, String body, EmailAttachmentContent attachment);

    record EmailAttachmentContent(String fileName, String contentType, byte[] content) {

        public EmailAttachmentContent {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(content, "content");
        }
    }
}
