package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.model.PurgeOutboxStatus;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.persistence.SpringPages;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaPurgeOutboxRepositoryAdapter implements PurgeOutboxRepository {

    private static final Set<PurgeOutboxStatus> RETRYABLE =
            Set.of(PurgeOutboxStatus.PENDING, PurgeOutboxStatus.FAILED);

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
        return repository.claim(id, PurgeOutboxStatus.PROCESSING, RETRYABLE) == 1;
    }

    @Override
    @Transactional
    public void markCompleted(UUID id) {
        repository.markCompleted(id, PurgeOutboxStatus.COMPLETED);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error) {
        String trimmed = error == null ? "purge failed" : error;
        if (trimmed.length() > 1024) {
            trimmed = trimmed.substring(0, 1024);
        }
        repository.markFailed(id, PurgeOutboxStatus.FAILED, trimmed);
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
}
