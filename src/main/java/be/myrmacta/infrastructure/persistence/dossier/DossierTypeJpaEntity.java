package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.PieceType;
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

    @Column(name = "sla_target_hours")
    private Integer slaTargetHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "document_template_keys", nullable = false, columnDefinition = "json")
    private List<String> documentTemplateKeys = List.of();

    // TODO §106 (D-PIECE-TYPES) — types de pièces justificatives du type.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "piece_types", nullable = false, columnDefinition = "json")
    private List<PieceType> pieceTypes = List.of();

    @Column(name = "created_by", nullable = false, length = 128)
    private String createdBy;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

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
            boolean retentionNever,
            Integer slaTargetHours,
            List<String> documentTemplateKeys,
            List<PieceType> pieceTypes,
            String createdBy,
            String updatedBy) {
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
        this.slaTargetHours = slaTargetHours;
        this.documentTemplateKeys = List.copyOf(documentTemplateKeys);
        this.pieceTypes = List.copyOf(pieceTypes);
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
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
                type.retentionNever(),
                type.slaTargetHours(),
                type.documentTemplateKeys(),
                type.pieceTypes(),
                type.createdBy(),
                type.updatedBy());
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
                retentionNever,
                slaTargetHours,
                documentTemplateKeys,
                pieceTypes,
                createdBy,
                updatedBy);
    }
}
