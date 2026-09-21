package com.flowforge.infrastructure.persistence.reporting;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.time.Instant;
import java.util.UUID;

public record SlaAlertCandidateRow(
        UUID id,
        String reference,
        String dossierTypeCode,
        Instant createdAt,
        Instant closedAt,
        Instant updatedAt,
        DossierStatus status,
        Integer slaTargetHours) {}
