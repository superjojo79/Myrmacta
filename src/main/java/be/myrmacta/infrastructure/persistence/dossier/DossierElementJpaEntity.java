package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.ElementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "dossier_element")
public class DossierElementJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ElementType type;

    @Column(nullable = false)
    private String label;

    @Column(name = "storage_ref", length = 512)
    private String storageRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> metadata = Map.of();

    @Column(name = "added_by", nullable = false)
    private String addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "workflow_step_id", length = 128)
    private String workflowStepId;

    protected DossierElementJpaEntity() {}

    public static DossierElementJpaEntity fromDomain(DossierElement element) {
        DossierElementJpaEntity entity = new DossierElementJpaEntity();
        entity.id = element.id();
        entity.dossierId = element.dossierId();
        entity.tenantId = element.tenantId();
        entity.type = element.type();
        entity.label = element.label();
        entity.storageRef = element.storageRef();
        entity.metadata = element.metadata();
        entity.addedBy = element.addedBy();
        entity.addedAt = element.addedAt();
        entity.workflowStepId = element.workflowStepId();
        return entity;
    }

    public DossierElement toDomain() {
        return new DossierElement(
                id, dossierId, tenantId, type, label, storageRef, metadata, addedBy, addedAt, workflowStepId);
    }
}
