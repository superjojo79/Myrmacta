package be.myrmacta.infrastructure.web.notification.template;

/** Corps d'un POST ou PUT de gabarit de notification au-delà du plafond (issue #43). */
class NotificationTemplatePayloadTooLargeException extends RuntimeException {

    NotificationTemplatePayloadTooLargeException(long maxBodyBytes) {
        super("Notification template request body exceeds " + maxBodyBytes + " bytes");
    }
}
