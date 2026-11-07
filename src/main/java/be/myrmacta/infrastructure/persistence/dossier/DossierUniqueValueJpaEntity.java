package be.myrmacta.infrastructure.persistence.dossier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** TODO §102 (D-UNIQUE-STORE) — valeur « unique » tenue par un dossier non clos (changeset 037). Écrite par requêtes. */
@Entity
@Table(name = "dossier_unique_value")
@IdClass(DossierUniqueValueJpaEntity.Key.class)
class DossierUniqueValueJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Id
    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Id
    @Column(name = "field_value", nullable = false, length = 500)
    private String fieldValue;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    protected DossierUniqueValueJpaEntity() {
    }

    static class Key implements Serializable {
        private String tenantId;
        private String dossierTypeCode;
        private String fieldName;
        private String fieldValue;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(tenantId, k.tenantId)
                    && Objects.equals(dossierTypeCode, k.dossierTypeCode)
                    && Objects.equals(fieldName, k.fieldName) && Objects.equals(fieldValue, k.fieldValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, dossierTypeCode, fieldName, fieldValue);
        }
    }
}
