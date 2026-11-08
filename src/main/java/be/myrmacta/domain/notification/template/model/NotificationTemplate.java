package be.myrmacta.domain.notification.template.model;

import be.myrmacta.domain.document.model.TemplateStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Gabarit de notification versionné (registre §123) : un objet et un corps texte, rendus en mode TEXT, et un corps HTML
 * facultatif rendu en mode HTML. Clé de résolution : {@code (templateKey, variantKey, locale)}, une seule version active
 * par clé.
 */
public record NotificationTemplate(
        UUID id,
        String templateKey,
        String variantKey,
        String locale,
        String label,
        int version,
        TemplateStatus status,
        String subject,
        String textBody,
        String htmlBody,
        String tenantId,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy) {

    public NotificationTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public NotificationTemplate withStatus(TemplateStatus newStatus, Instant at, String by) {
        return new NotificationTemplate(
                id, templateKey, variantKey, locale, label, version, newStatus, subject, textBody, htmlBody, tenantId,
                createdAt, at, by);
    }

    /** Copie sans les corps (liste) : l'objet reste, les corps deviennent vides. */
    public NotificationTemplate withoutBodies() {
        return new NotificationTemplate(
                id, templateKey, variantKey, locale, label, version, status, subject, "", null, tenantId, createdAt,
                updatedAt, updatedBy);
    }
}
