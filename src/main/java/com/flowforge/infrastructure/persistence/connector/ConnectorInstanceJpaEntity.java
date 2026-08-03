package com.flowforge.infrastructure.persistence.connector;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "connector_instance")
class ConnectorInstanceJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "connector_type", nullable = false, length = 128)
    private String connectorType;

    @Column(nullable = false)
    private boolean enabled;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Map<String, Object> config = Map.of();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConnectorInstanceJpaEntity() {}

    static ConnectorInstanceJpaEntity fromDomain(ConnectorInstanceData data, Map<String, Object> encryptedConfig) {
        Instant now = Instant.now();
        ConnectorInstanceJpaEntity entity = new ConnectorInstanceJpaEntity();
        entity.id = data.id();
        entity.tenantId = data.tenantId();
        entity.name = data.name();
        entity.connectorType = data.connectorType();
        entity.enabled = data.enabled();
        entity.config = encryptedConfig == null ? Map.of() : Map.copyOf(encryptedConfig);
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    void applyDomain(ConnectorInstanceData data, Map<String, Object> encryptedConfig) {
        this.name = data.name();
        this.connectorType = data.connectorType();
        this.enabled = data.enabled();
        this.config = encryptedConfig == null ? Map.of() : Map.copyOf(encryptedConfig);
        this.updatedAt = Instant.now();
    }

    void markDeleted(Instant when) {
        this.deletedAt = when;
        this.enabled = false;
        this.updatedAt = when;
    }

    void clearDeleted() {
        this.deletedAt = null;
        this.updatedAt = Instant.now();
    }

    ConnectorInstanceData toDomain(Map<String, Object> decryptedConfig) {
        return new ConnectorInstanceData(
                id, tenantId, name, connectorType, enabled, decryptedConfig);
    }

    UUID id() {
        return id;
    }

    String tenantId() {
        return tenantId;
    }

    Instant deletedAt() {
        return deletedAt;
    }

    Map<String, Object> config() {
        return config;
    }
}
