package be.myrmacta.domain.reporting.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SlaAlertState(String tenantId, UUID dossierId, SlaStatus lastStatus, Instant updatedAt) {

    public SlaAlertState {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(lastStatus, "lastStatus");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
