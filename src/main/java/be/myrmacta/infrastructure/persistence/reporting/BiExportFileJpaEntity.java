package be.myrmacta.infrastructure.persistence.reporting;

import be.myrmacta.domain.reporting.model.BiExportFile;
import be.myrmacta.domain.reporting.model.BiExportKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bi_export_file")
class BiExportFileJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BiExportKind kind;

    @Column(name = "dossier_type_code", length = 64)
    private String dossierTypeCode;

    @Column(name = "storage_ref", nullable = false, length = 512)
    private String storageRef;

    @Column(name = "file_name", nullable = false, length = 256)
    private String fileName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    protected BiExportFileJpaEntity() {
        // JPA
    }

    private BiExportFileJpaEntity(
            UUID id,
            String tenantId,
            BiExportKind kind,
            String dossierTypeCode,
            String storageRef,
            String fileName,
            Instant createdAt,
            long sizeBytes) {
        this.id = id;
        this.tenantId = tenantId;
        this.kind = kind;
        this.dossierTypeCode = dossierTypeCode;
        this.storageRef = storageRef;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.sizeBytes = sizeBytes;
    }

    static BiExportFileJpaEntity fromDomain(BiExportFile file) {
        return new BiExportFileJpaEntity(
                file.id(),
                file.tenantId(),
                file.kind(),
                file.dossierTypeCode(),
                file.storageRef(),
                file.fileName(),
                file.createdAt(),
                file.sizeBytes());
    }

    BiExportFile toDomain() {
        return new BiExportFile(id, tenantId, kind, dossierTypeCode, storageRef, fileName, createdAt, sizeBytes);
    }
}
