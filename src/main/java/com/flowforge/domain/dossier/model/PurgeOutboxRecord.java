package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One purge request. {@code dossierId} carries a UNIQUE constraint so a conflicting insert is a
 * no-op. Completed rows are kept (D-KEEP-OUTBOX).
 */
public record PurgeOutboxRecord(
        UUID id,
        UUID dossierId,
        String eventType,
        PurgeOutboxStatus status,
        PurgeOutboxOrigin origin,
        String payloadJson,
        Instant createdAt,
        String lastError) {

    public PurgeOutboxRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static PurgeOutboxRecord pending(
            UUID id,
            UUID dossierId,
            String eventType,
            PurgeOutboxOrigin origin,
            String payloadJson,
            Instant createdAt) {
        return new PurgeOutboxRecord(
                id, dossierId, eventType, PurgeOutboxStatus.PENDING, origin, payloadJson, createdAt, null);
    }
}
