package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.model.PurgeOutboxStatus;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaPurgeOutboxRepositoryAdapter implements PurgeOutboxRepository {

    private static final Set<PurgeOutboxStatus> RETRYABLE =
            Set.of(PurgeOutboxStatus.PENDING, PurgeOutboxStatus.FAILED);
    private static final Set<PurgeOutboxStatus> UNFINISHED =
            Set.of(PurgeOutboxStatus.PENDING, PurgeOutboxStatus.PROCESSING, PurgeOutboxStatus.FAILED);

    private final SpringDataPurgeOutboxRepository repository;

    public JpaPurgeOutboxRepositoryAdapter(SpringDataPurgeOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void insert(PurgeOutboxRecord record) {
        if (repository.existsByDossierId(record.dossierId())) {
            return;
        }
        repository.saveAndFlush(PurgeOutboxJpaEntity.fromDomain(record));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDossierId(UUID dossierId) {
        return repository.existsByDossierId(dossierId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurgeOutboxRecord> findRetryable(int limit) {
        return repository
                .findByStatusInOrderByCreatedAtAsc(RETRYABLE, PageRequest.of(0, Math.max(limit, 1)))
                .stream()
                .map(PurgeOutboxJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public boolean claim(UUID id) {
        return repository.claim(id, PurgeOutboxStatus.PROCESSING, RETRYABLE, Instant.now()) == 1;
    }

    @Override
    @Transactional
    public void markCompleted(UUID id) {
        repository.markCompleted(id, PurgeOutboxStatus.COMPLETED, Instant.now());
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error) {
        String trimmed = error == null ? "purge failed" : error;
        if (trimmed.length() > 1024) {
            trimmed = trimmed.substring(0, 1024);
        }
        repository.markFailed(id, PurgeOutboxStatus.FAILED, trimmed, Instant.now());
    }

    @Override
    @Transactional
    public int requeueStaleProcessing(Instant olderThan) {
        return repository.requeueStaleProcessing(
                PurgeOutboxStatus.FAILED,
                PurgeOutboxStatus.PROCESSING,
                "Stuck in PROCESSING past the recovery timeout; requeued for retry.",
                Instant.now(),
                olderThan);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<PurgeOutboxRecord> page(PurgeOutboxStatus statusFilter, PageQuery query) {
        var pageable = SpringPages.of(query);
        var page = statusFilter == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(statusFilter, pageable);
        return SpringPages.map(page, PurgeOutboxJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByStatus(PurgeOutboxStatus status) {
        return repository.countByStatus(status);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> oldestUnfinishedCreatedAt() {
        return repository.findOldestCreatedAtByStatusIn(UNFINISHED);
    }
}
