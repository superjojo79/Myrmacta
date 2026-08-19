package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One retention-purge request produced by {@code EvaluateDossierRetentionUseCase}.
 * {@code dossierId} carries a UNIQUE constraint (D-OUTBOX-UNIQ) so a conflicting insert is a
 * no-op — idempotent under concurrent job runs. No relay consumes this table in this lot (§22).
 */
public record PurgeOutboxRecord(
        UUID id,
        UUID dossierId,
        String eventType,
        PurgeOutboxStatus status,
        String payloadJson,
        Instant createdAt) {

    public PurgeOutboxRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(payloadJson, "payloadJson");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
