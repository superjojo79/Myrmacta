package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.PurgeOutboxOrigin;
import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "purge_outbox",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_purge_outbox_dossier_id",
                columnNames = {"dossier_id"}))
class PurgeOutboxJpaEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurgeOutboxStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PurgeOutboxOrigin origin;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected PurgeOutboxJpaEntity() {
        // JPA
    }

    private PurgeOutboxJpaEntity(
            UUID id,
            UUID dossierId,
            String eventType,
            PurgeOutboxStatus status,
            PurgeOutboxOrigin origin,
            String payloadJson,
            Instant createdAt,
            Instant updatedAt,
            String lastError) {
        this.id = id;
        this.dossierId = dossierId;
        this.eventType = eventType;
        this.status = status;
        this.origin = origin;
        this.payloadJson = payloadJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastError = lastError;
    }

    static PurgeOutboxJpaEntity fromDomain(PurgeOutboxRecord record) {
        return new PurgeOutboxJpaEntity(
                record.id(),
                record.dossierId(),
                record.eventType(),
                record.status(),
                record.origin(),
                record.payloadJson(),
                record.createdAt(),
                record.createdAt(),
                record.lastError());
    }

    PurgeOutboxRecord toDomain() {
        return new PurgeOutboxRecord(
                id, dossierId, eventType, status, origin, payloadJson, createdAt, lastError);
    }
}
