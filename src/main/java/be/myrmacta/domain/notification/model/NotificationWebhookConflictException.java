package be.myrmacta.domain.notification.model;

public class NotificationWebhookConflictException extends RuntimeException {

    public NotificationWebhookConflictException(String message) {
        super(message);
    }
}
