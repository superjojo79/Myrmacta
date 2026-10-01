package com.flowforge.infrastructure.persistence.notification;

import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationOutboxRecord;
import com.flowforge.domain.notification.model.NotificationOutboxStatus;
import com.flowforge.domain.notification.port.out.NotificationOutboxRepository;
import com.flowforge.domain.notification.port.out.PendingOutboxWrite;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaNotificationOutboxRepositoryAdapter implements NotificationOutboxRepository {

    /** Durée pendant laquelle une ligne prise n'est plus proposée à un autre exécutant (au-delà : exécutant présumé mort). */
    static final java.time.Duration CLAIM_LEASE = java.time.Duration.ofMinutes(5);

    private final SpringDataNotificationOutboxRepository repository;

    JpaNotificationOutboxRepositoryAdapter(SpringDataNotificationOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public UUID savePending(String tenantId, NotificationChannel channel, String payloadJson, Instant now) {
        return savePendingBatch(tenantId, List.of(new PendingOutboxWrite(channel, payloadJson)), now).getFirst();
    }

    @Override
    @Transactional
    public List<UUID> savePendingBatch(String tenantId, List<PendingOutboxWrite> writes, Instant now) {
        Objects.requireNonNull(writes, "writes");
        if (writes.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = new ArrayList<>(writes.size());
        List<NotificationOutboxJpaEntity> entities = new ArrayList<>(writes.size());
        for (PendingOutboxWrite write : writes) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            entities.add(NotificationOutboxJpaEntity.pending(
                    id, tenantId, write.channel(), write.payloadJson(), now));
        }
        repository.saveAll(entities);
        repository.flush();
        return List.copyOf(ids);
    }

    /**
     * D5 (TODO §79) : renvoie uniquement les lignes que <b>cet</b> exécutant a prises (bail de {@link #CLAIM_LEASE}).
     * Deux pods qui interrogent en même temps se partagent les lignes au lieu de les envoyer chacun.
     */
    // Volontairement SANS transaction englobante : chaque prise est son propre UPDATE atomique (méthode transactionnelle du
    // dépôt). Une transaction unique verrouillerait plusieurs lignes dans un ordre variable et provoquerait des interblocages
    // entre exécutants concurrents.
    @Override
    public List<NotificationOutboxRecord> lockNextPending(int batchSize, Instant now) {
        Instant leaseUntil = now.plus(CLAIM_LEASE);
        List<NotificationOutboxRecord> claimed = new ArrayList<>();
        for (NotificationOutboxJpaEntity candidate : repository.findEligiblePending(
                NotificationOutboxStatus.PENDING, now, PageRequest.of(0, Math.max(batchSize, 0)))) {
            NotificationOutboxRecord record = candidate.toDomain();
            if (repository.claim(record.id(), NotificationOutboxStatus.PENDING, record.nextAttemptAt(), leaseUntil, now) == 1) {
                claimed.add(record);
            }
        }
        return List.copyOf(claimed);
    }

    @Override
    @Transactional
    public void markProcessed(UUID id, Instant now) {
        NotificationOutboxJpaEntity entity = repository.findById(id).orElseThrow();
        entity.markProcessed(now);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void markDead(UUID id, String error, Instant now) {
        NotificationOutboxJpaEntity entity = repository.findById(id).orElseThrow();
        entity.markDead(error, now);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void recordFailure(UUID id, String error, int maxAttempts, Instant now) {
        NotificationOutboxJpaEntity entity = repository.findById(id).orElseThrow();
        entity.recordFailure(error, maxAttempts, now);
        repository.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public int purgeTerminalOlderThan(Instant cutoff) {
        return repository.deleteTerminalOlderThan(
                List.of(NotificationOutboxStatus.PROCESSED, NotificationOutboxStatus.DEAD), cutoff);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> oldestPendingCreatedAt() {
        return repository.findOldestCreatedAtByStatus(NotificationOutboxStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByChannelAndStatus(NotificationChannel channel, NotificationOutboxStatus status) {
        return repository.countByChannelAndStatus(channel, status);
    }
}
