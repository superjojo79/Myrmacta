package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DayCount;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** TODO §115 (D-DEADLINE-FIELD) — réglage d'échéance d'un type de dossier (changesets 045 et 046). Clé : tenant et type. */
@Entity
@Table(name = "dossier_type_deadline")
@IdClass(DossierTypeDeadlineJpaEntity.Key.class)
class DossierTypeDeadlineJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "reminder_days", nullable = false)
    private int reminderDays;

    @Column(name = "escalation_group", length = 64)
    private String escalationGroup;

    @Column(name = "reminder_day_count", nullable = false, length = 16)
    private String reminderDayCount;

    protected DossierTypeDeadlineJpaEntity() {}

    static DossierTypeDeadlineJpaEntity fromDomain(DeadlineSetting setting) {
        DossierTypeDeadlineJpaEntity entity = new DossierTypeDeadlineJpaEntity();
        entity.tenantId = setting.tenantId();
        entity.dossierTypeCode = setting.dossierTypeCode();
        entity.applyDomain(setting);
        return entity;
    }

    void applyDomain(DeadlineSetting setting) {
        this.fieldName = setting.fieldName();
        this.reminderDays = setting.reminderDays();
        this.escalationGroup = setting.escalationGroup();
        this.reminderDayCount = setting.reminderDayCount().name();
    }

    DeadlineSetting toDomain() {
        return new DeadlineSetting(tenantId, dossierTypeCode, fieldName, reminderDays, escalationGroup, DayCount.parse(reminderDayCount));
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
