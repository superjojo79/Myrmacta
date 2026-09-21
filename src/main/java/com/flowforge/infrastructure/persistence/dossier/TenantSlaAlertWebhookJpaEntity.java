package com.flowforge.infrastructure.persistence.dossier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tenant_sla_alert_webhook")
@IdClass(TenantSlaAlertWebhookJpaEntity.Key.class)
class TenantSlaAlertWebhookJpaEntity {

    @Id
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Id
    @Column(name = "webhook_id", nullable = false)
    private UUID webhookId;

    protected TenantSlaAlertWebhookJpaEntity() {
        // JPA
    }

    TenantSlaAlertWebhookJpaEntity(String tenantId, UUID webhookId) {
        this.tenantId = tenantId;
        this.webhookId = webhookId;
    }

    UUID webhookId() {
        return webhookId;
    }

    static final class Key implements Serializable {
        public String tenantId;
        public UUID webhookId;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(tenantId, key.tenantId) && Objects.equals(webhookId, key.webhookId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(tenantId, webhookId);
        }
    }
}
