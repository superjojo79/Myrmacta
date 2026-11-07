package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow de réponse d'un type de dossier (changeset 044). Clé : tenant et type. */
@Entity
@Table(name = "dossier_type_reply_workflow")
@IdClass(DossierTypeReplyWorkflowJpaEntity.Key.class)
class DossierTypeReplyWorkflowJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Column(nullable = false, length = 16)
    private String mode;

    @Column(name = "workflow_key", nullable = false)
    private String workflowKey;

    @Column(name = "child_type_code", length = 64)
    private String childTypeCode;

    protected DossierTypeReplyWorkflowJpaEntity() {}

    static DossierTypeReplyWorkflowJpaEntity fromDomain(ReplyWorkflowSetting setting) {
        DossierTypeReplyWorkflowJpaEntity entity = new DossierTypeReplyWorkflowJpaEntity();
        entity.tenantId = setting.tenantId();
        entity.dossierTypeCode = setting.dossierTypeCode();
        entity.applyDomain(setting);
        return entity;
    }

    void applyDomain(ReplyWorkflowSetting setting) {
        this.mode = setting.mode().name();
        this.workflowKey = setting.workflowKey();
        this.childTypeCode = setting.childTypeCode();
    }

    ReplyWorkflowSetting toDomain() {
        return new ReplyWorkflowSetting(tenantId, dossierTypeCode, ReplyWorkflowSetting.Mode.valueOf(mode), workflowKey, childTypeCode);
    }

    /** Clé composite : le tenant et le code du type. */
    static class Key implements Serializable {

        private String tenantId;
        private String dossierTypeCode;

        protected Key() {}

        Key(String tenantId, String dossierTypeCode) {
            this.tenantId = tenantId;
            this.dossierTypeCode = dossierTypeCode;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(tenantId, key.tenantId) && Objects.equals(dossierTypeCode, key.dossierTypeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, dossierTypeCode);
        }
    }
}
