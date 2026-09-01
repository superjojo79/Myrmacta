package com.flowforge.domain.connector.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks the last cron firing of a {@code scheduler}-type connector instance. Deliberately kept
 * separate from {@link ConnectorInstanceRepository}/{@code ConnectorInstanceData} — this state is
 * relevant only to one connector type, not the shared connector instance model.
 */
public interface ConnectorScheduleStateRepository {

    Optional<Instant> findLastTriggeredAt(UUID connectorInstanceId);

    /** Upsert: creates the row on first firing, overwrites the timestamp otherwise. */
    void upsert(UUID connectorInstanceId, Instant triggeredAt);
}
