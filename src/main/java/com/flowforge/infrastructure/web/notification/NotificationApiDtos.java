package com.flowforge.infrastructure.web.notification;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record CreateNotificationWebhookRequest(
        String name, String targetUrl, String secret, Boolean enabled) {}

record UpdateNotificationWebhookRequest(
        String name, String targetUrl, String secret, Boolean enabled, Boolean restore) {}

record NotificationWebhookResponse(
        UUID id,
        String tenantId,
        String name,
        String targetUrl,
        String secret,
        boolean secretConfigured,
        boolean enabled,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    static NotificationWebhookResponse from(NotificationWebhookConfig config) {
        NotificationWebhookConfig masked = config.withMaskedSecret();
        return new NotificationWebhookResponse(
                masked.id(),
                masked.tenantId(),
                masked.name(),
                masked.targetUrl(),
                masked.secretCiphertext(),
                config.secretCiphertext() != null && !config.secretCiphertext().isBlank(),
                masked.enabled(),
                masked.deletedAt(),
                masked.createdAt(),
                masked.updatedAt());
    }
}

record SendNotificationRequest(
        List<String> channels,
        String to,
        String subject,
        String body,
        String userId,
        UUID webhookConfigId,
        Map<String, Object> payload) {

    NotificationEvent toEvent() {
        List<NotificationChannel> parsed = channels == null
                ? List.of()
                : channels.stream().map(NotificationChannel::parse).toList();
        return new NotificationEvent(parsed, to, subject, body, userId, webhookConfigId, payload);
    }
}

record SendNotificationResponse(List<UUID> outboxIds) {}
