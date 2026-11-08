package be.myrmacta.domain.notification.template.model;

import java.util.Objects;

/**
 * Résultat du rendu d'un gabarit. {@code htmlBody} est nul quand le gabarit n'a pas de corps HTML.
 *
 * <p>{@link #toString()} n'imprime aucun contenu : un message rendu porte des valeurs du dossier.
 */
public record RenderedNotification(String subject, String textBody, String htmlBody) {

    public RenderedNotification {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
    }

    @Override
    public String toString() {
        return "RenderedNotification[subjectChars=%d, textChars=%d, html=%s]"
                .formatted(subject.length(), textBody.length(), htmlBody != null);
    }
}
