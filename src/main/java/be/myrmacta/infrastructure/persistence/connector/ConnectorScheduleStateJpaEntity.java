package be.myrmacta.infrastructure.persistence.connector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "connector_schedule_state")
class ConnectorScheduleStateJpaEntity {

    @Id
    @Column(name = "connector_instance_id", nullable = false)
    private UUID connectorInstanceId;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    protected ConnectorScheduleStateJpaEntity() {
        // JPA
    }

    ConnectorScheduleStateJpaEntity(UUID connectorInstanceId, Instant lastTriggeredAt) {
        this.connectorInstanceId = connectorInstanceId;
        this.lastTriggeredAt = lastTriggeredAt;
    }

    UUID connectorInstanceId() {
        return connectorInstanceId;
    }

    Instant lastTriggeredAt() {
        return lastTriggeredAt;
    }
}
