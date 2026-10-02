package com.flowforge.infrastructure.persistence.parcours;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "parcours")
class ParcoursJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    String tenantId;

    @Column(name = "parcours_key", nullable = false, length = 64)
    String key;

    @Column(name = "label", nullable = false, length = 200)
    String label;

    @Column(name = "definition", nullable = false)
    String definition;

    @Column(name = "version", nullable = false)
    int version;

    @Column(name = "created_by", nullable = false, length = 128)
    String createdBy;

    @Column(name = "updated_by", nullable = false, length = 128)
    String updatedBy;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Column(name = "last_compiled_at")
    Instant lastCompiledAt;

    @Column(name = "last_compiled_sha256", length = 64)
    String lastCompiledSha256;

    protected ParcoursJpaEntity() {}
}
