package com.flowforge.infrastructure.persistence.connector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "tenant_connector_dek")
class TenantConnectorDekJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "dek_wrapped", nullable = false, length = 512)
    private String dekWrapped;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TenantConnectorDekJpaEntity() {}

    TenantConnectorDekJpaEntity(String tenantId, String dekWrapped, int keyVersion, Instant updatedAt) {
        this.tenantId = tenantId;
        this.dekWrapped = dekWrapped;
        this.keyVersion = keyVersion;
        this.updatedAt = updatedAt;
    }

    String tenantId() {
        return tenantId;
    }

    String dekWrapped() {
        return dekWrapped;
    }
}
