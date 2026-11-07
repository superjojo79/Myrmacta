package be.myrmacta.domain.notification.template.model;

import java.util.UUID;

public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(TemplateResolutionKey key) {
        super("No ACTIVE notification template for key=%s variant=%s locale=%s"
                .formatted(key.templateKey(), key.variantKey(), key.locale()));
    }

    public NotificationTemplateNotFoundException(UUID id) {
        super("Notification template not found: " + id);
    }
}
