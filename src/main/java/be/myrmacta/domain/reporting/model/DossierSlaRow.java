package be.myrmacta.domain.reporting.model;

import be.myrmacta.domain.dossier.model.DossierStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DossierSlaRow(
        UUID id,
        String typeCode,
        Instant createdAt,
        Instant closedAt,
        Instant updatedAt,
        DossierStatus status) {

    public DossierSlaRow {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(typeCode, "typeCode");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
    }

    public boolean closed() {
        return status == DossierStatus.CLOTURE || status == DossierStatus.ARCHIVE;
    }
}
