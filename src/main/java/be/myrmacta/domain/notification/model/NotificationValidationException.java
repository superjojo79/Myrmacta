package be.myrmacta.domain.notification.model;

public class NotificationValidationException extends RuntimeException {

    public NotificationValidationException(String message) {
        super(message);
    }
}
