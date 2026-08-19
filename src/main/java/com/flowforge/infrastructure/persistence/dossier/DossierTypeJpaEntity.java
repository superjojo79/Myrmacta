package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "dossier_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dossier_type_tenant_code",
                columnNames = {"tenant_id", "code"}))
public class DossierTypeJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "workflow_key", nullable = false, length = 128)
    private String workflowKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "allow_unknown_fields", nullable = false)
    private boolean allowUnknownFields;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_definitions", nullable = false, columnDefinition = "json")
    private List<FieldDefinition> fieldDefinitions = List.of();

    @Column(name = "retention_days")
    private Integer retentionDays;

    @Column(name = "retention_never", nullable = false)
    private boolean retentionNever;

    protected DossierTypeJpaEntity() {
        // JPA
    }

    private DossierTypeJpaEntity(
            UUID id,
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.label = label;
        this.workflowKey = workflowKey;
        this.active = active;
        this.allowUnknownFields = allowUnknownFields;
        this.fieldDefinitions = List.copyOf(fieldDefinitions);
        this.retentionDays = retentionDays;
        this.retentionNever = retentionNever;
    }

    static DossierTypeJpaEntity fromDomain(DossierType type) {
        return new DossierTypeJpaEntity(
                type.id(),
                type.tenantId(),
                type.code(),
                type.label(),
                type.workflowKey(),
                type.active(),
                type.allowUnknownFields(),
                type.fieldDefinitions(),
                type.retentionDays(),
                type.retentionNever());
    }

    DossierType toDomain() {
        return new DossierType(
                id,
                tenantId,
                code,
                label,
                workflowKey,
                active,
                allowUnknownFields,
                fieldDefinitions,
                retentionDays,
                retentionNever);
    }
}
