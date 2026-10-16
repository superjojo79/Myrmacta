package com.flowforge.infrastructure.persistence.user;

import com.flowforge.domain.user.model.KnownUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "known_user",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_known_user_tenant_user",
                columnNames = {"tenant_id", "user_id"}))
class KnownUserJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "display_name", nullable = false, length = 200)
    private String displayName;

    @Column(length = 320)
    private String email;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<String> roles = List.of();

    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;

    protected KnownUserJpaEntity() {
        // JPA
    }

    static KnownUserJpaEntity create(KnownUser user) {
        KnownUserJpaEntity entity = new KnownUserJpaEntity();
        entity.id = UUID.randomUUID();
        entity.tenantId = user.tenantId();
        entity.userId = user.userId();
        entity.refresh(user);
        return entity;
    }

    void refresh(KnownUser user) {
        this.displayName = user.displayName();
        this.email = user.email();
        this.roles = user.roles().stream().sorted().toList();
        this.lastLoginAt = user.lastLoginAt();
    }

    KnownUser toDomain() {
        return new KnownUser(
                tenantId,
                userId,
                displayName,
                email,
                roles == null ? Set.of() : new HashSet<>(roles),
                lastLoginAt);
    }
}
