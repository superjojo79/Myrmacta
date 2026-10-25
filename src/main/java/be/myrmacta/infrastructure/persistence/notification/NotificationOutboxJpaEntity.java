package be.myrmacta.infrastructure.persistence.notification;

import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationOutboxRecord;
import be.myrmacta.domain.notification.model.NotificationOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_outbox")
class NotificationOutboxJpaEntity {

    private static final int MAX_ERROR_LENGTH = 1024;

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationOutboxStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected NotificationOutboxJpaEntity() {}

    static NotificationOutboxJpaEntity pending(
            UUID id, String tenantId, NotificationChannel channel, String payloadJson, Instant now) {
        NotificationOutboxJpaEntity entity = new NotificationOutboxJpaEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.channel = channel;
        entity.status = NotificationOutboxStatus.PENDING;
        entity.payloadJson = payloadJson;
        entity.attempts = 0;
        entity.nextAttemptAt = now;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    NotificationOutboxRecord toDomain() {
        return new NotificationOutboxRecord(
                id,
                tenantId,
                channel,
                status,
                payloadJson,
                attempts,
                nextAttemptAt,
                lastError,
                createdAt,
                updatedAt,
                processedAt);
    }

    void markProcessed(Instant now) {
        this.status = NotificationOutboxStatus.PROCESSED;
        this.processedAt = now;
        this.updatedAt = now;
        this.lastError = null;
    }

    void markDead(String error, Instant now) {
        this.status = NotificationOutboxStatus.DEAD;
        this.lastError = truncate(error);
        this.processedAt = now;
        this.updatedAt = now;
    }

    void recordFailure(String error, int maxAttempts, Instant now) {
        this.attempts++;
        this.lastError = truncate(error);
        this.updatedAt = now;
        if (this.attempts >= maxAttempts) {
            this.status = NotificationOutboxStatus.DEAD;
            this.processedAt = now;
        } else {
            this.status = NotificationOutboxStatus.PENDING;
            this.nextAttemptAt = now.plus(backoff(this.attempts));
        }
    }

    static Duration backoff(int attemptsAfterIncrement) {
        return switch (attemptsAfterIncrement) {
            case 1 -> Duration.ofSeconds(1);
            case 2 -> Duration.ofSeconds(2);
            default -> Duration.ofSeconds(4);
        };
    }

    static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }

    UUID id() {
        return id;
    }
}
