package com.flowforge.domain.reporting.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Scored or not: a null {@code slaTargetHours} is ignored by the alert scan. */
public record SlaAlertCandidate(
        UUID id,
        String reference,
        String dossierTypeCode,
        Instant createdAt,
        Instant closedAt,
        Instant updatedAt,
        boolean closed,
        Integer slaTargetHours) {

    public SlaAlertCandidate {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
