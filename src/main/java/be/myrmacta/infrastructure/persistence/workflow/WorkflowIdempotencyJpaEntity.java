package be.myrmacta.infrastructure.persistence.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workflow_idempotency")
@IdClass(WorkflowIdempotencyJpaEntity.IdempotencyId.class)
class WorkflowIdempotencyJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkflowIdempotencyJpaEntity() {
        // JPA
    }

    WorkflowIdempotencyJpaEntity(
            String tenantId, UUID dossierId, String idempotencyKey, UUID linkId, Instant createdAt) {
        this.tenantId = tenantId;
        this.dossierId = dossierId;
        this.idempotencyKey = idempotencyKey;
        this.linkId = linkId;
        this.createdAt = createdAt;
    }

    UUID linkId() {
        return linkId;
    }

    static final class IdempotencyId implements Serializable {
        private String tenantId;
        private UUID dossierId;
        private String idempotencyKey;

        IdempotencyId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof IdempotencyId that)) {
                return false;
            }
            return Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(dossierId, that.dossierId)
                    && Objects.equals(idempotencyKey, that.idempotencyKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, dossierId, idempotencyKey);
        }
    }
}
