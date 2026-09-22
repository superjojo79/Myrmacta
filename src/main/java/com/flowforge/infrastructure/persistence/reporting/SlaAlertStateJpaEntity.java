package com.flowforge.infrastructure.persistence.reporting;

import com.flowforge.domain.reporting.model.SlaAlertState;
import com.flowforge.domain.reporting.model.SlaStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "sla_alert_state")
@IdClass(SlaAlertStateJpaEntity.Key.class)
class SlaAlertStateJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_status", nullable = false, length = 32)
    private SlaStatus lastStatus;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SlaAlertStateJpaEntity() {
        // JPA
    }

    private SlaAlertStateJpaEntity(String tenantId, UUID dossierId, SlaStatus lastStatus, Instant updatedAt) {
        this.tenantId = tenantId;
        this.dossierId = dossierId;
        this.lastStatus = lastStatus;
        this.updatedAt = updatedAt;
    }

    static SlaAlertStateJpaEntity fromDomain(SlaAlertState state) {
        return new SlaAlertStateJpaEntity(state.tenantId(), state.dossierId(), state.lastStatus(), state.updatedAt());
    }

    SlaAlertState toDomain() {
        return new SlaAlertState(tenantId, dossierId, lastStatus, updatedAt);
    }

    static final class Key implements Serializable {
        public String tenantId;
        public UUID dossierId;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(tenantId, key.tenantId) && Objects.equals(dossierId, key.dossierId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, dossierId);
        }
    }
}
