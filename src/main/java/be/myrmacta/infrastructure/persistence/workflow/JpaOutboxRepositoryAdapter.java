package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.OutboxEvent;
import be.myrmacta.domain.workflow.model.OutboxStatus;
import be.myrmacta.domain.workflow.model.TriggerOutboxPayload;
import be.myrmacta.domain.workflow.port.out.OutboxRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaOutboxRepositoryAdapter implements OutboxRepository {

    private final SpringDataOutboxEventRepository repository;

    JpaOutboxRepositoryAdapter(SpringDataOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public OutboxEvent savePending(TriggerOutboxPayload payload) {
        return repository.saveAndFlush(OutboxEventJpaEntity.fromDomain(OutboxEvent.pending(payload, Instant.now())))
                .toDomain();
    }

    /** Durée pendant laquelle une ligne prise n'est plus proposée à un autre exécutant (au-delà : exécutant présumé mort). */
    static final java.time.Duration CLAIM_LEASE = java.time.Duration.ofMinutes(5);

    // SANS transaction englobante : chaque prise est un UPDATE atomique distinct (pas d'interblocage entre exécutants).
    @Override
    public List<OutboxEvent> lockNextPending(int batchSize) {
        Instant now = Instant.now();
        Instant leaseUntil = now.plus(CLAIM_LEASE);
        List<OutboxEvent> claimed = new java.util.ArrayList<>();
        for (OutboxEventJpaEntity candidate : repository.findClaimable(
                OutboxStatus.PENDING, now, org.springframework.data.domain.PageRequest.of(0, Math.max(batchSize, 0)))) {
            OutboxEvent event = candidate.toDomain();
            if (repository.claim(event.eventId(), OutboxStatus.PENDING, now, leaseUntil) == 1) {
                claimed.add(event);
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    @Transactional
    public void markProcessed(UUID eventId) {
        OutboxEventJpaEntity entity = repository.findById(eventId).orElseThrow();
        entity.markProcessed(Instant.now());
    }

    @Override
    @Transactional
    public void markDead(UUID eventId, String error) {
        OutboxEventJpaEntity entity = repository.findById(eventId).orElseThrow();
        entity.markDead(error);
    }

    @Override
    @Transactional
    public void recordFailure(UUID eventId, String error, int maxAttempts) {
        OutboxEventJpaEntity entity = repository.findById(eventId).orElseThrow();
        entity.recordFailure(error, maxAttempts);
    }

    @Override
    @Transactional
    public int purgeProcessedOlderThanDays(int days) {
        return repository.deleteByStatusAndProcessedAtBefore(
                OutboxStatus.PROCESSED, Instant.now().minus(days, ChronoUnit.DAYS));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> oldestPendingCreatedAt() {
        return repository.findOldestCreatedAtByStatus(OutboxStatus.PENDING);
    }
}
