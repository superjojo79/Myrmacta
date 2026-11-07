package be.myrmacta.infrastructure.persistence.dossier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

/** Compteur de références de dossier par tenant, type et année (changeset 001). Écrit par requête native atomique. */
@Entity
@Table(name = "dossier_reference_seq")
@IdClass(DossierReferenceSeqJpaEntity.Key.class)
class DossierReferenceSeqJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Id
    @Column(name = "\"year\"", nullable = false)
    private int year;

    @Column(name = "last_value", nullable = false)
    private long lastValue;

    protected DossierReferenceSeqJpaEntity() {
    }

    static class Key implements Serializable {
        private String tenantId;
        private String dossierTypeCode;
        private int year;

        public Key() {
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && year == k.year && Objects.equals(tenantId, k.tenantId)
                    && Objects.equals(dossierTypeCode, k.dossierTypeCode);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, dossierTypeCode, year);
        }
    }
}
