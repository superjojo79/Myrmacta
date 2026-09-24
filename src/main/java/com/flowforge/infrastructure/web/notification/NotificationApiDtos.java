package com.flowforge.infrastructure.web.notification;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.model.NotificationWebhookConfig;
import com.flowforge.domain.notification.model.UserNotificationRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.ObjectMapper;

record CreateNotificationWebhookRequest(
        String name,
        String targetUrl,
        @Schema(writeOnly = true) String secret,
        Boolean enabled) {}

record UpdateNotificationWebhookRequest(
        String name,
        String targetUrl,
        @Schema(writeOnly = true) String secret,
        Boolean enabled,
        Boolean restore) {}

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

/** TODO §66 F3: server-truth unread badge count, exposed by {@code GET /api/notifications/unread-count}. */
record UnreadCountResponse(long count) {}

record UserNotificationResponse(UUID id, Map<String, Object> payload, Instant createdAt, Instant readAt, boolean read) {

    @SuppressWarnings("unchecked")
    static UserNotificationResponse from(UserNotificationRecord record, ObjectMapper objectMapper) {
        return new UserNotificationResponse(
                record.id(),
                objectMapper.readValue(record.payloadJson(), Map.class),
                record.createdAt(),
                record.readAt(),
                record.isRead());
    }
}
