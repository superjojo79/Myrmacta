package com.flowforge.domain.notification.port.out;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationOutboxRepository {

    UUID savePending(String tenantId, NotificationChannel channel, String payloadJson, Instant now);

    /**
     * Persists all pending rows in a single transaction (fan-out atomicity).
     *
     * @return outbox ids in the same order as {@code writes}
     */
    List<UUID> savePendingBatch(String tenantId, List<PendingOutboxWrite> writes, Instant now);

    /**
     * Prend (bail court, voir l'adaptateur) puis renvoie les lignes éligibles que l'appelant est <b>seul</b> à traiter :
     * deux exécutants concurrents ne reçoivent jamais la même ligne (TODO §79, D5). Une ligne dont l'exécutant a disparu
     * redevient éligible à l'échéance du bail.
     */
    List<NotificationOutboxRecord> lockNextPending(int batchSize, Instant now);

    /**
     * Renouvelle, juste avant son traitement, le bail d'une ligne prise par {@link #lockNextPending} ({@code expectedLease} = le
     * {@code nextAttemptAt} de la ligne reçue). {@code false} si un autre exécutant l'a reprise entre-temps : l'appelant la saute.
     */
    boolean renewClaim(UUID id, Instant expectedLease, Instant now);

    void markProcessed(UUID id, Instant now);

    void markDead(UUID id, String error, Instant now);

    /**
     * Increments attempts; keeps PENDING with exponential backoff (1s/2s/4s by attempts)
     * or marks DEAD when {@code attempts >= maxAttempts}.
     */
    void recordFailure(UUID id, String error, int maxAttempts, Instant now);

    /** Deletes PROCESSED and DEAD rows older than {@code cutoff}. */
    int purgeTerminalOlderThan(Instant cutoff);

    Optional<Instant> oldestPendingCreatedAt();

    long countByChannelAndStatus(NotificationChannel channel, NotificationOutboxStatus status);
}
