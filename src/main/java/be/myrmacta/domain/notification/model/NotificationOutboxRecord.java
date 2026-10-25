package be.myrmacta.domain.notification.model;

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
        Instant processedAt) {

    /** Même ligne avec un autre {@code nextAttemptAt} (le jeton de prise renvoyé par {@code lockNextPending}). */
    public NotificationOutboxRecord withNextAttemptAt(Instant next) {
        return new NotificationOutboxRecord(
                id, tenantId, channel, status, payloadJson, attempts, next, lastError, createdAt, updatedAt, processedAt);
    }
}
