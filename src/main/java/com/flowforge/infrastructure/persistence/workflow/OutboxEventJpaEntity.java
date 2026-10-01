package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.OutboxEvent;
import com.flowforge.domain.workflow.model.OutboxStatus;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_event")
class OutboxEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private TriggerOutboxPayload payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    /** D5 : bail posé par l'exécutant qui a pris la ligne ; {@code null} = non louée. */
    @Column(name = "claimed_until")
    private Instant claimedUntil;

    protected OutboxEventJpaEntity() {
        // JPA
    }

    private OutboxEventJpaEntity(OutboxEvent event) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.payload = event.payload();
        this.status = event.status();
        this.attempts = event.attempts();
        this.lastError = event.lastError();
        this.createdAt = event.createdAt();
        this.processedAt = event.processedAt();
    }

    static OutboxEventJpaEntity fromDomain(OutboxEvent event) {
        return new OutboxEventJpaEntity(event);
    }

    OutboxEvent toDomain() {
        return new OutboxEvent(eventId, eventType, payload, status, attempts, lastError, createdAt, processedAt);
    }

    void markProcessed(Instant at) {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = at;
        this.lastError = null;
        this.claimedUntil = null;
    }

    void markDead(String error) {
        this.status = OutboxStatus.DEAD;
        this.lastError = error;
        this.claimedUntil = null;
    }

    void recordFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = error;
        this.claimedUntil = null;
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.DEAD;
        }
    }
}
