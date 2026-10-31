package be.myrmacta.domain.notification.port.out;

import java.util.Objects;

public interface EmailPort {

    /** E-mail texte sans pièce jointe ; renvoie son Message-ID ({@code null} s'il est inconnu). */
    default String send(String to, String subject, String body) {
        return send(to, subject, body, null);
    }

    /**
     * Issue #183 — e-mail texte avec une pièce jointe ({@code null} : aucune). Le contenu a été relu dans le stockage du
     * tenant et contrôlé (document produit du dossier, PDF, plafond) avant d'arriver ici. Renvoie le Message-ID posé à l'envoi
     * ({@code null} s'il est inconnu), inscrit dans l'index des fils (TODO §113).
     */
    String send(String to, String subject, String body, EmailAttachmentContent attachment);

    record EmailAttachmentContent(String fileName, String contentType, byte[] content) {

        public EmailAttachmentContent {
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(contentType, "contentType");
            Objects.requireNonNull(content, "content");
        }
    }
}
