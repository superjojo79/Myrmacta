package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.OutboxEvent;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    OutboxEvent savePending(TriggerOutboxPayload payload);

    List<OutboxEvent> lockNextPending(int batchSize);

    void markProcessed(UUID eventId);

    void markDead(UUID eventId, String error);

    void recordFailure(UUID eventId, String error, int maxAttempts);

    int purgeProcessedOlderThanDays(int days);

    Optional<Instant> oldestPendingCreatedAt();
}
