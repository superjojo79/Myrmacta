package be.myrmacta.domain.notification.template.model;

import java.util.UUID;

public class NotificationTemplateNotFoundException extends RuntimeException {

    public NotificationTemplateNotFoundException(TemplateResolutionKey key) {
        // La variante et la langue peuvent venir d'une variable de process : jamais recopiées dans le message.
        super("No ACTIVE notification template for key=%s".formatted(key.templateKey()));
    }

    public NotificationTemplateNotFoundException(UUID id) {
        super("Notification template not found: " + id);
    }
}
