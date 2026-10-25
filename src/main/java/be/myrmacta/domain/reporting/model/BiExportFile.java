package be.myrmacta.domain.reporting.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BiExportFile(
        UUID id,
        String tenantId,
        BiExportKind kind,
        String dossierTypeCode,
        String storageRef,
        String fileName,
        Instant createdAt,
        long sizeBytes) {

    public BiExportFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(kind, "kind");
        if (kind == BiExportKind.DOSSIERS) {
            Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        }
        Objects.requireNonNull(storageRef, "storageRef");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("sizeBytes must be >= 0");
        }
    }
}
