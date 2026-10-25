package be.myrmacta.domain.document.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DocumentTemplate(
        UUID id,
        String templateKey,
        String variantKey,
        String locale,
        String label,
        int version,
        TemplateStatus status,
        String htmlContent,
        String tenantId,
        Instant createdAt,
        Instant updatedAt,
        String updatedBy
) {
    public DocumentTemplate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(htmlContent, "htmlContent");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
