package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.OutboxEvent;
import com.flowforge.domain.workflow.model.OutboxStatus;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
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

    @Override
    @Transactional
    public List<OutboxEvent> lockNextPending(int batchSize) {
        return repository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).stream()
                .limit(batchSize)
                .map(OutboxEventJpaEntity::toDomain)
                .toList();
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
