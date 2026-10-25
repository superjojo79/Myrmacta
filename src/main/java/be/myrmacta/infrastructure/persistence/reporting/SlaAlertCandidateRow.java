package be.myrmacta.infrastructure.persistence.reporting;

import be.myrmacta.domain.dossier.model.DossierStatus;
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
