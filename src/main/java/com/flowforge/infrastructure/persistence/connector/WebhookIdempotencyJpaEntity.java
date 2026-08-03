package com.flowforge.infrastructure.persistence.connector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "webhook_idempotency")
@IdClass(WebhookIdempotencyJpaEntity.Pk.class)
class WebhookIdempotencyJpaEntity {

    @Id
    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Id
    @Column(name = "idem_key", nullable = false, length = 255)
    private String idemKey;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookIdempotencyJpaEntity() {}

    WebhookIdempotencyJpaEntity(UUID instanceId, String idemKey, int responseStatus, Instant createdAt) {
        this.instanceId = instanceId;
        this.idemKey = idemKey;
        this.responseStatus = responseStatus;
        this.createdAt = createdAt;
    }

    int responseStatus() {
        return responseStatus;
    }

    void responseStatus(int responseStatus) {
        this.responseStatus = responseStatus;
    }

    Instant createdAt() {
        return createdAt;
    }

    static final class Pk implements Serializable {
        private UUID instanceId;
        private String idemKey;

        Pk() {}

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk that)) {
                return false;
            }
            return Objects.equals(instanceId, that.instanceId) && Objects.equals(idemKey, that.idemKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(instanceId, idemKey);
        }
    }
}
