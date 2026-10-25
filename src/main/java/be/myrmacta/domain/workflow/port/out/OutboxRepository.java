package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.workflow.model.OutboxEvent;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxRepository {

    OutboxEvent savePending(TriggerOutboxPayload payload);

    /**
     * Prend (bail court) puis renvoie les événements que l'appelant est <b>seul</b> à traiter : deux exécutants concurrents ne
     * reçoivent jamais le même (TODO §79, D5).
     */
    List<OutboxEvent> lockNextPending(int batchSize);

    void markProcessed(UUID eventId);

    void markDead(UUID eventId, String error);

    void recordFailure(UUID eventId, String error, int maxAttempts);

    int purgeProcessedOlderThanDays(int days);

    Optional<Instant> oldestPendingCreatedAt();
}
