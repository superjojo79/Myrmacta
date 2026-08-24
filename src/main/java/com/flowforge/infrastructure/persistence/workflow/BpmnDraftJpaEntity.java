package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.BpmnDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "bpmn_draft")
@IdClass(BpmnDraftJpaEntity.BpmnDraftId.class)
class BpmnDraftJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "process_definition_key", nullable = false, length = 255)
    private String processDefinitionKey;

    @Column(name = "xml", nullable = false)
    private String xml;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 128)
    private String updatedBy;

    protected BpmnDraftJpaEntity() {
        // JPA
    }

    private BpmnDraftJpaEntity(
            String tenantId, String processDefinitionKey, String xml, Instant updatedAt, String updatedBy) {
        this.tenantId = tenantId;
        this.processDefinitionKey = processDefinitionKey;
        this.xml = xml;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    static BpmnDraftJpaEntity fromDomain(BpmnDraft draft) {
        return new BpmnDraftJpaEntity(
                draft.tenantId(), draft.processDefinitionKey(), draft.xml(), draft.updatedAt(), draft.updatedBy());
    }

    BpmnDraft toDomain() {
        return new BpmnDraft(tenantId, processDefinitionKey, xml, updatedAt, updatedBy);
    }

    static final class BpmnDraftId implements Serializable {
        private String tenantId;
        private String processDefinitionKey;

        BpmnDraftId() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BpmnDraftId that)) {
                return false;
            }
            return Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(processDefinitionKey, that.processDefinitionKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, processDefinitionKey);
        }
    }
}
