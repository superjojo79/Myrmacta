package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository {

    UUID savePending(String tenantId, NotificationChannel channel, String payloadJson, Instant now);

    /**
     * Persists all pending rows in a single transaction (fan-out atomicity).
     *
     * @return outbox ids in the same order as {@code writes}
     */
    List<UUID> savePendingBatch(String tenantId, List<PendingOutboxWrite> writes, Instant now);

    List<NotificationOutboxRecord> lockNextPending(int batchSize, Instant now);

    void markProcessed(UUID id, Instant now);

    void markDead(UUID id, String error, Instant now);

    /**
     * Increments attempts; keeps PENDING with exponential backoff (1s/2s/4s by attempts)
     * or marks DEAD when {@code attempts >= maxAttempts}.
     */
    void recordFailure(UUID id, String error, int maxAttempts, Instant now);

    /** Deletes PROCESSED and DEAD rows older than {@code cutoff}. */
    int purgeTerminalOlderThan(Instant cutoff);
}
