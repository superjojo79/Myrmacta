package com.flowforge.domain.notification.model;

import java.time.Instant;
import java.util.UUID;

public record NotificationOutboxRecord(
        UUID id,
        String tenantId,
        NotificationChannel channel,
        NotificationOutboxStatus status,
        String payloadJson,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt,
        Instant processedAt) {}
