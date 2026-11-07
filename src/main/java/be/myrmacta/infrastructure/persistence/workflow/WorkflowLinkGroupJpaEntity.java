package be.myrmacta.infrastructure.persistence.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** TODO §114 (D-GROUP-LINK) — groupe destinataire d'un lien de workflow (changeset 042). Supprimé avec le lien (cascade de la base). */
@Entity
@Table(name = "workflow_link_group")
class WorkflowLinkGroupJpaEntity {

    @Id
    @Column(name = "link_id", nullable = false)
    private UUID linkId;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "group_code", nullable = false, length = 64)
    private String groupCode;

    protected WorkflowLinkGroupJpaEntity() {}

    static WorkflowLinkGroupJpaEntity of(String tenantId, UUID linkId, String groupCode) {
        WorkflowLinkGroupJpaEntity entity = new WorkflowLinkGroupJpaEntity();
        entity.linkId = linkId;
        entity.tenantId = tenantId;
        entity.groupCode = groupCode;
        return entity;
    }

    void assign(String groupCode) {
        this.groupCode = groupCode;
    }

    String groupCode() {
        return groupCode;
    }
}
