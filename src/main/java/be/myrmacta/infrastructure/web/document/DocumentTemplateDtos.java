package be.myrmacta.infrastructure.web.document;

import be.myrmacta.domain.document.model.DocumentTemplate;
import be.myrmacta.domain.document.model.TemplateStatus;
import java.time.Instant;
import java.util.UUID;

final class DocumentTemplateDtos {

    private DocumentTemplateDtos() {}

    record CreateRequest(
            String templateKey,
            String variantKey,
            String locale,
            String label,
            String htmlContent) {}

    record UpdateRequest(String label, String htmlContent) {}

    record TemplateResponse(
            UUID id,
            String templateKey,
            String variantKey,
            String locale,
            String label,
            int version,
            TemplateStatus status,
            String htmlContent,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy) {

        static TemplateResponse from(DocumentTemplate template) {
            return new TemplateResponse(
                    template.id(),
                    template.templateKey(),
                    template.variantKey(),
                    template.locale(),
                    template.label(),
                    template.version(),
                    template.status(),
                    template.htmlContent(),
                    template.createdAt(),
                    template.updatedAt(),
                    template.updatedBy());
        }
    }
}
