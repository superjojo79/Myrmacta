package be.myrmacta.infrastructure.persistence.notification;

import be.myrmacta.domain.notification.model.UserNotificationRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_notification")
class UserNotificationJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected UserNotificationJpaEntity() {}

    static UserNotificationJpaEntity create(
            UUID id, String tenantId, String recipient, String payloadJson, Instant now) {
        UserNotificationJpaEntity entity = new UserNotificationJpaEntity();
        entity.id = id;
        entity.tenantId = tenantId;
        entity.recipient = recipient;
        entity.payloadJson = payloadJson;
        entity.createdAt = now;
        return entity;
    }

    UserNotificationRecord toDomain() {
        return new UserNotificationRecord(id, tenantId, recipient, payloadJson, createdAt, readAt);
    }

    UUID id() {
        return id;
    }

    // TODO §66 F7 (Phase 10 adversarial review): idempotent — a repeat markRead on an already-read
    // row (e.g. a stale client re-sending a click, or two tabs racing) must not keep sliding
    // readAt forward on every call.
    void markRead(Instant now) {
        if (this.readAt == null) {
            this.readAt = now;
        }
    }
}
