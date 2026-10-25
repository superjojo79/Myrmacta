package be.myrmacta.infrastructure.persistence.notification;

import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_webhook")
class NotificationWebhookJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(name = "secret_ciphertext", length = 2048)
    private String secretCiphertext;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationWebhookJpaEntity() {}

    static NotificationWebhookJpaEntity fromDomain(NotificationWebhookConfig config, String secretAtRest) {
        NotificationWebhookJpaEntity entity = new NotificationWebhookJpaEntity();
        entity.id = config.id();
        entity.tenantId = config.tenantId();
        entity.name = config.name();
        entity.targetUrl = config.targetUrl();
        entity.secretCiphertext = secretAtRest;
        entity.enabled = config.enabled();
        entity.deletedAt = config.deletedAt();
        entity.createdAt = config.createdAt();
        entity.updatedAt = config.updatedAt();
        return entity;
    }

    void applyDomain(NotificationWebhookConfig config, String secretAtRest) {
        this.name = config.name();
        this.targetUrl = config.targetUrl();
        this.secretCiphertext = secretAtRest;
        this.enabled = config.enabled();
        this.deletedAt = config.deletedAt();
        this.updatedAt = config.updatedAt();
    }

    void markDeleted(Instant when) {
        this.deletedAt = when;
        this.enabled = false;
        this.updatedAt = when;
    }

    NotificationWebhookConfig toDomain() {
        return new NotificationWebhookConfig(
                id,
                tenantId,
                name,
                targetUrl,
                secretCiphertext,
                enabled,
                deletedAt,
                createdAt,
                updatedAt);
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
}
