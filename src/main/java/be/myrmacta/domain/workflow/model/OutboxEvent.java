package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String eventType,
        TriggerOutboxPayload payload,
        OutboxStatus status,
        int attempts,
        String lastError,
        Instant createdAt,
        Instant processedAt
) {
    public OutboxEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static OutboxEvent pending(TriggerOutboxPayload payload, Instant createdAt) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "TriggerOutboxPayload",
                payload,
                OutboxStatus.PENDING,
                0,
                null,
                createdAt,
                null);
    }
}
