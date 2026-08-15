package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DossierRelation(
        UUID id,
        UUID sourceDossierId,
        UUID targetDossierId,
        String tenantId,
        RelationType type,
        String createdBy,
        Instant createdAt,
        String idempotencyKey
) {
    public DossierRelation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceDossierId, "sourceDossierId");
        Objects.requireNonNull(targetDossierId, "targetDossierId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sourceDossierId.equals(targetDossierId)) {
            throw new IllegalArgumentException("source and target must differ");
        }
    }
}
