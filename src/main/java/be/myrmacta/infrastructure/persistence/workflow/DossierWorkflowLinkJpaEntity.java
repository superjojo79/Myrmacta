package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dossier_workflow_link")
class DossierWorkflowLinkJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "process_instance_id", length = 128)
    private String processInstanceId;

    @Column(name = "process_definition_key", nullable = false, length = 128)
    private String processDefinitionKey;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false, length = 64)
    private String status;

    @Column(name = "trigger_ref", nullable = false)
    private String triggerRef;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    /** D5 : bail de réconciliation (non domaine) ; {@code null} = non pris. */
    @Column(name = "reconcile_claimed_until")
    private Instant reconcileClaimedUntil;

    @Column(name = "end_reason", length = 64)
    private String endReason;

    @Column(name = "started_by", length = 128)
    private String startedBy;

    protected DossierWorkflowLinkJpaEntity() {
        // JPA
    }

    private DossierWorkflowLinkJpaEntity(DossierWorkflowLink link) {
        this.id = link.id();
        this.dossierId = link.dossierId();
        this.tenantId = link.tenantId();
        this.processInstanceId = link.processInstanceId();
        this.processDefinitionKey = link.processDefinitionKey();
        this.sequence = link.sequence();
        this.status = link.status();
        this.triggerRef = link.triggerRef();
        this.startedAt = link.startedAt();
        this.endedAt = link.endedAt();
        this.endReason = link.endReason();
        this.startedBy = link.startedBy();
    }

    static DossierWorkflowLinkJpaEntity fromDomain(DossierWorkflowLink link) {
        return new DossierWorkflowLinkJpaEntity(link);
    }

    DossierWorkflowLink toDomain() {
        return new DossierWorkflowLink(
                id,
                dossierId,
                tenantId,
                processInstanceId,
                processDefinitionKey,
                sequence,
                status,
                triggerRef,
                startedAt,
                endedAt,
                endReason,
                startedBy);
    }
}
