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
        EmailAttachment attachment,
        /** TODO §113 (D-MAILROOM-INDEX) — dossier d'où part l'e-mail, posé par la brique « Notifier » seule ; {@code null} sinon. */
        UUID originDossierId,
        /** Issue #19 (TODO §123) — corps HTML rendu d'un gabarit, EMAIL seulement ; {@code null} : e-mail texte. */
        String htmlBody) {

    /** Sans corps HTML (tous les producteurs antérieurs au gabarit de notification). */
    public NotificationEvent(
            List<NotificationChannel> channels,
            String to,
            String subject,
            String body,
            String userId,
            UUID webhookConfigId,
            Map<String, Object> payload,
            DossierFieldsSnapshot dossierFieldsSnapshot,
            EmailAttachment attachment,
            UUID originDossierId) {
        this(channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, attachment,
                originDossierId, null);
    }

    public NotificationEvent(
            List<NotificationChannel> channels,
            String to,
            String subject,
            String body,
            String userId,
            UUID webhookConfigId,
            Map<String, Object> payload,
            DossierFieldsSnapshot dossierFieldsSnapshot,
            EmailAttachment attachment) {
        this(channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, attachment, null);
    }

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
                channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, emailAttachment, originDossierId, htmlBody);
    }

    /** Même événement, envoyé depuis ce dossier : un e-mail parti est inscrit dans l'index des fils (TODO §113). */
    public NotificationEvent withOriginDossier(UUID dossierId) {
        return new NotificationEvent(
                channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, attachment, dossierId, htmlBody);
    }

    /** Même événement, avec ce corps HTML ({@code null} : aucun) ; l'e-mail part alors en {@code multipart/alternative}. */
    public NotificationEvent withHtmlBody(String html) {
        return new NotificationEvent(
                channels, to, subject, body, userId, webhookConfigId, payload, dossierFieldsSnapshot, attachment,
                originDossierId, html);
    }
}
