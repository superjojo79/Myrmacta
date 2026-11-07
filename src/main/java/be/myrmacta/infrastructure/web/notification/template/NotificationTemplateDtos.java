package be.myrmacta.infrastructure.web.notification.template;

import be.myrmacta.domain.document.model.TemplateStatus;
import be.myrmacta.domain.notification.template.model.NotificationTemplate;
import be.myrmacta.domain.notification.template.model.RenderedNotification;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

final class NotificationTemplateDtos {

    private NotificationTemplateDtos() {}

    record NotificationTemplateCreateRequest(
            String templateKey,
            String variantKey,
            String locale,
            String label,
            String subject,
            String textBody,
            String htmlBody) {}

    /** Un champ absent est inchangé ; {@code htmlBody} vide supprime le corps HTML. */
    record NotificationTemplateUpdateRequest(String label, String subject, String textBody, String htmlBody) {}

    /** Soit {@code id} (gabarit stocké, brouillon compris), soit {@code template} (contenu fourni). */
    record NotificationTemplatePreviewRequest(UUID id, NotificationTemplatePreviewContent template, Map<String, Object> model) {}

    record NotificationTemplatePreviewContent(String subject, String textBody, String htmlBody) {}

    record NotificationTemplatePreviewResponse(String subject, String textBody, String htmlBody) {

        static NotificationTemplatePreviewResponse from(RenderedNotification rendered) {
            return new NotificationTemplatePreviewResponse(rendered.subject(), rendered.textBody(), rendered.htmlBody());
        }
    }

    record NotificationTemplateResponse(
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
            Instant createdAt,
            Instant updatedAt,
            String updatedBy) {

        static NotificationTemplateResponse from(NotificationTemplate template) {
            return new NotificationTemplateResponse(
                    template.id(),
                    template.templateKey(),
                    template.variantKey(),
                    template.locale(),
                    template.label(),
                    template.version(),
                    template.status(),
                    template.subject(),
                    template.textBody(),
                    template.htmlBody(),
                    template.createdAt(),
                    template.updatedAt(),
                    template.updatedBy());
        }
    }
}
