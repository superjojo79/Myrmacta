package be.myrmacta.infrastructure.persistence.notification;

import be.myrmacta.domain.notification.model.NotificationChannel;
import be.myrmacta.domain.notification.model.NotificationOutboxRecord;
import be.myrmacta.domain.notification.model.NotificationOutboxStatus;
import be.myrmacta.domain.notification.port.out.NotificationOutboxRepository;
import be.myrmacta.domain.notification.port.out.PendingOutboxWrite;
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
                // Le jeton de prise (valeur de next_attempt_at) accompagne la ligne : il sert au renouvellement avant traitement.
                claimed.add(record.withNextAttemptAt(leaseUntil));
            }
        }
        return List.copyOf(claimed);
    }

    // Chaque issue est un UPDATE conditionné à l'état « en attente » (revue D5) : un exécutant en retard — dont le bail a
    // expiré et dont la ligne a été reprise — ne peut ni ressusciter ni écraser une ligne déjà traitée.
    @Override
    public void markProcessed(UUID id, Instant now) {
        repository.finish(id, NotificationOutboxStatus.PENDING, NotificationOutboxStatus.PROCESSED, null, now);
    }

    @Override
    public void markDead(UUID id, String error, Instant now) {
        repository.finish(id, NotificationOutboxStatus.PENDING, NotificationOutboxStatus.DEAD,
                NotificationOutboxJpaEntity.truncate(error), now);
    }

    @Override
    public void recordFailure(UUID id, String error, int maxAttempts, Instant now) {
        if (repository.bumpAttempts(id, NotificationOutboxStatus.PENDING, NotificationOutboxJpaEntity.truncate(error), now) != 1) {
            return; // ligne déjà traitée par un autre exécutant
        }
        int attempts = repository.findById(id).orElseThrow().toDomain().attempts();
        if (attempts >= maxAttempts) {
            repository.finish(id, NotificationOutboxStatus.PENDING, NotificationOutboxStatus.DEAD,
                    NotificationOutboxJpaEntity.truncate(error), now);
        } else {
            repository.reschedule(id, NotificationOutboxStatus.PENDING, now.plus(NotificationOutboxJpaEntity.backoff(attempts)), now);
        }
    }

    @Override
    public boolean renewClaim(UUID id, Instant expectedLease, Instant now) {
        return repository.renew(id, NotificationOutboxStatus.PENDING, expectedLease, now.plus(CLAIM_LEASE), now) == 1;
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
