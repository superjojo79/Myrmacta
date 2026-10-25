package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(
        name = "dossier_type_acl",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_dossier_type_acl_subject",
                columnNames = {"tenant_id", "dossier_type_code", "subject_user_id"}))
public class DossierTypeAclJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dossier_type_code", nullable = false, length = 64)
    private String dossierTypeCode;

    @Column(name = "subject_user_id", nullable = false, length = 128)
    private String subjectUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DossierPermission permission;

    protected DossierTypeAclJpaEntity() {
        // JPA
    }

    private DossierTypeAclJpaEntity(
            String tenantId,
            String dossierTypeCode,
            String subjectUserId,
            DossierPermission permission) {
        this.tenantId = tenantId;
        this.dossierTypeCode = dossierTypeCode;
        this.subjectUserId = subjectUserId;
        this.permission = permission;
    }

    static DossierTypeAclJpaEntity fromDomain(DossierTypeAclEntry entry) {
        return new DossierTypeAclJpaEntity(
                entry.tenantId(),
                entry.dossierTypeCode(),
                entry.subjectUserId(),
                entry.permission());
    }

    void updatePermission(DossierPermission permission) {
        this.permission = permission;
    }

    DossierTypeAclEntry toDomain() {
        return new DossierTypeAclEntry(tenantId, dossierTypeCode, subjectUserId, permission);
    }
}
