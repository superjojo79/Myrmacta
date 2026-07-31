package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "dossier",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dossier_tenant_reference",
                columnNames = {"tenant_id", "reference"}))
public class DossierJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DossierStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> fields = Map.of();

    @Column(name = "current_workflow_link_id")
    private UUID currentWorkflowLinkId;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "closed_by", length = 128)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected DossierJpaEntity() {
        // JPA
    }

    private DossierJpaEntity(
            UUID id,
            String tenantId,
            String reference,
            String dossierTypeCode,
            String label,
            DossierStatus status,
            Map<String, Object> fields,
            UUID currentWorkflowLinkId,
            long version,
            String createdBy,
            Instant createdAt,
            String updatedBy,
            Instant updatedAt,
            String closedBy,
            Instant closedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.dossierTypeCode = dossierTypeCode;
        this.label = label;
        this.status = status;
        this.fields = Map.copyOf(fields);
        this.currentWorkflowLinkId = currentWorkflowLinkId;
        this.version = version;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
        this.closedBy = closedBy;
        this.closedAt = closedAt;
    }

    static DossierJpaEntity fromDomain(Dossier dossier) {
        return new DossierJpaEntity(
                dossier.id(),
                dossier.tenantId(),
                dossier.reference(),
                dossier.dossierTypeCode(),
                dossier.label(),
                dossier.status(),
                dossier.fields(),
                dossier.currentWorkflowLinkId(),
                dossier.version(),
                dossier.createdBy(),
                dossier.createdAt(),
                dossier.updatedBy(),
                dossier.updatedAt(),
                dossier.closedBy(),
                dossier.closedAt());
    }

    Dossier toDomain() {
        return new Dossier(
                id,
                tenantId,
                reference,
                dossierTypeCode,
                label,
                status,
                fields,
                currentWorkflowLinkId,
                version,
                createdBy,
                createdAt,
                updatedBy,
                updatedAt,
                closedBy,
                closedAt);
    }
}
