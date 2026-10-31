package be.myrmacta.infrastructure.persistence.user;

import be.myrmacta.domain.user.model.UserGroup;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** TODO §113 — groupe d'utilisateurs et ses membres listés (changeset 041). */
@Entity
@Table(
        name = "user_group",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_group_tenant_code", columnNames = {"tenant_id", "code"}))
class UserGroupJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(name = "idp_group", length = 256)
    private String idpGroup;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_group_member", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "user_id", nullable = false, length = 128)
    private Set<String> memberIds = new HashSet<>();

    protected UserGroupJpaEntity() {
        // JPA
    }

    static UserGroupJpaEntity create(UserGroup group) {
        UserGroupJpaEntity entity = new UserGroupJpaEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = group.tenantId();
        entity.code = group.code();
        entity.refresh(group);
        return entity;
    }

    void refresh(UserGroup group) {
        this.label = group.label();
        this.idpGroup = group.idpGroup();
        this.memberIds.retainAll(group.memberIds());
        this.memberIds.addAll(group.memberIds());
    }

    UserGroup toDomain() {
        return new UserGroup(tenantId, code, label, idpGroup, Set.copyOf(memberIds));
    }
}
