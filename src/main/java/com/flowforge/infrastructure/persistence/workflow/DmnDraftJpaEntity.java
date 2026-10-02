package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.model.Provenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "dmn_draft")
@IdClass(DmnDraftJpaEntity.DmnDraftId.class)
class DmnDraftJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "decision_key", nullable = false, length = 255)
    private String decisionKey;

    @Column(name = "xml", nullable = false)
    private String xml;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    @Column(name = "provenance", nullable = false, length = 16)
    private String provenance = Provenance.UI.name();

    @Column(name = "note", length = 500)
    private String note;

    @Column(name = "pilot_user_id", length = 128)
    private String pilotUserId;

    protected DmnDraftJpaEntity() {
        // JPA
    }

    private DmnDraftJpaEntity(
            String tenantId,
            String decisionKey,
            String xml,
            Instant updatedAt,
            String updatedBy,
            String provenance,
            String note,
            String pilotUserId) {
        this.tenantId = tenantId;
        this.decisionKey = decisionKey;
        this.xml = xml;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.provenance = provenance;
        this.note = note;
        this.pilotUserId = pilotUserId;
    }

    static DmnDraftJpaEntity fromDomain(DmnDraft draft) {
        return new DmnDraftJpaEntity(
                draft.tenantId(),
                draft.decisionKey(),
                draft.xml(),
                draft.updatedAt(),
                draft.updatedBy(),
                draft.provenance().name(),
                draft.note(),
                draft.pilotUserId());
    }

    DmnDraft toDomain() {
        return new DmnDraft(
                tenantId, decisionKey, xml, updatedAt, updatedBy, Provenance.valueOf(provenance), note, pilotUserId);
    }

    static final class DmnDraftId implements Serializable {
        private String tenantId;
        private String decisionKey;

        DmnDraftId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DmnDraftId that)) {
                return false;
            }
            return Objects.equals(tenantId, that.tenantId) && Objects.equals(decisionKey, that.decisionKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, decisionKey);
        }
    }
}
