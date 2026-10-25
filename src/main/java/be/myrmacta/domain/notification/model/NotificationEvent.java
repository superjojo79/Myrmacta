package be.myrmacta.domain.notification.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fan-out request for one or more notification channels. Validation of required
 * per-channel fields is performed by {@code SendNotificationUseCase}.
 */
public record NotificationEvent(
        List<NotificationChannel> channels,
        String to,
        String subject,
        String body,
        String userId,
        UUID webhookConfigId,
        Map<String, Object> payload,
        DossierFieldsSnapshot dossierFieldsSnapshot,
        EmailAttachment attachment) {

    /** Sans pièce jointe (issue #183 : seule la brique « Notifier » en pose une). */
    public NotificationEvent(
            List<NotificationChannel> channels,
            String to,
            String subject,
            String body,
            String userId,
            UUID webhookConfigId,
            Map<String, Object> payload,
            DossierFieldsSnapshot dossierFieldsSnapshot) {
        this(channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, null);
    }

    /** Convenience — no dossier fields carried in the payload (today's only producers). */
    public NotificationEvent(
            List<NotificationChannel> channels,
            String to,
            String subject,
            String body,
            String userId,
            UUID webhookConfigId,
            Map<String, Object> payload) {
        this(channels, to, subject, body, userId, webhookConfigId, payload, null, null);
    }

    /** Même événement, avec cette pièce jointe e-mail ({@code null} : aucune). */
    public NotificationEvent withAttachment(EmailAttachment emailAttachment) {
        return new NotificationEvent(
                channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, emailAttachment);
    }
}
