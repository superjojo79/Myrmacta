package com.flowforge.domain.notification.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * {@code payloadJson} is kept as a raw JSON string, not parsed — same convention as
 * {@link NotificationOutboxRecord#payloadJson()}. Domain must not depend on a JSON library
 * (ArchUnit); structured access (if ever needed) belongs to the infrastructure layer.
 */
public record UserNotificationRecord(
        UUID id, String tenantId, String recipient, String payloadJson, Instant createdAt, Instant readAt) {

    public UserNotificationRecord {
        Objects.requireNonNull(payloadJson, "payloadJson");
    }

    public boolean isRead() {
        return readAt != null;
    }
}
