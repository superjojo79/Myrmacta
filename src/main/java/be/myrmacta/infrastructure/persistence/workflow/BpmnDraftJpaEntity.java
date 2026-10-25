package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.BpmnDraft;
import be.myrmacta.domain.workflow.model.Provenance;
import be.myrmacta.domain.workflow.model.ProposedWorkflowConfig;
import tools.jackson.databind.ObjectMapper;
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

    @Column(name = "provenance", nullable = false, length = 16)
    private String provenance = Provenance.UI.name();

    @Column(name = "proposed_config")
    private String proposedConfig;

    @Column(name = "pilot_user_id", length = 128)
    private String pilotUserId;

    @Column(name = "config_provenance", length = 16)
    private String configProvenance;

    @Column(name = "config_author", length = 128)
    private String configAuthor;

    protected BpmnDraftJpaEntity() {
        // JPA
    }

    private BpmnDraftJpaEntity(
            String tenantId,
            String processDefinitionKey,
            String xml,
            Instant updatedAt,
            String updatedBy,
            String provenance,
            String proposedConfig,
            String pilotUserId,
            String configProvenance,
            String configAuthor) {
        this.configProvenance = configProvenance;
        this.configAuthor = configAuthor;
        this.tenantId = tenantId;
        this.processDefinitionKey = processDefinitionKey;
        this.xml = xml;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.provenance = provenance;
        this.proposedConfig = proposedConfig;
        this.pilotUserId = pilotUserId;
    }

    static BpmnDraftJpaEntity fromDomain(BpmnDraft draft, ObjectMapper mapper) {
        return new BpmnDraftJpaEntity(
                draft.tenantId(),
                draft.processDefinitionKey(),
                draft.xml(),
                draft.updatedAt(),
                draft.updatedBy(),
                draft.provenance().name(),
                draft.proposedConfig() == null ? null : mapper.writeValueAsString(draft.proposedConfig()),
                draft.pilotUserId(),
                draft.configProvenance() == null ? null : draft.configProvenance().name(),
                draft.configAuthor());
    }

    BpmnDraft toDomain(ObjectMapper mapper) {
        return new BpmnDraft(
                tenantId,
                processDefinitionKey,
                xml,
                updatedAt,
                updatedBy,
                Provenance.valueOf(provenance),
                proposedConfig == null ? null : mapper.readValue(proposedConfig, ProposedWorkflowConfig.class),
                pilotUserId,
                configProvenance == null ? null : Provenance.valueOf(configProvenance),
                configAuthor);
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
