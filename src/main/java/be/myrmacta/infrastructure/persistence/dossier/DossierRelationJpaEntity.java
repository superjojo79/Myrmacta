package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierRelation;
import be.myrmacta.domain.dossier.model.RelationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "dossier_relation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dossier_relation",
                columnNames = {"tenant_id", "source_dossier_id", "target_dossier_id", "type"}))
public class DossierRelationJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "source_dossier_id", nullable = false)
    private UUID sourceDossierId;

    @Column(name = "target_dossier_id", nullable = false)
    private UUID targetDossierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RelationType type;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    protected DossierRelationJpaEntity() {}

    static DossierRelationJpaEntity fromDomain(DossierRelation relation) {
        DossierRelationJpaEntity entity = new DossierRelationJpaEntity();
        entity.id = relation.id();
        entity.tenantId = relation.tenantId();
        entity.sourceDossierId = relation.sourceDossierId();
        entity.targetDossierId = relation.targetDossierId();
        entity.type = relation.type();
        entity.createdBy = relation.createdBy();
        entity.createdAt = relation.createdAt();
        entity.idempotencyKey = relation.idempotencyKey();
        return entity;
    }

    DossierRelation toDomain() {
        return new DossierRelation(
                id, sourceDossierId, targetDossierId, tenantId, type, createdBy, createdAt, idempotencyKey);
    }
}
