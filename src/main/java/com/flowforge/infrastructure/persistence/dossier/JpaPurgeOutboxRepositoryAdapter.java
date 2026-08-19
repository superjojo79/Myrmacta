package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaPurgeOutboxRepositoryAdapter implements PurgeOutboxRepository {

    private final SpringDataPurgeOutboxRepository repository;

    public JpaPurgeOutboxRepositoryAdapter(SpringDataPurgeOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void insert(PurgeOutboxRecord record) {
        // D-OUTBOX-UNIQ: rely on UNIQUE(dossier_id) + exists check in the use case.
        // Do not catch DataIntegrityViolationException inside a joining TX — on PostgreSQL that
        // marks the session aborted and would poison the outer retention batch (Phase 7).
        // A rare concurrent conflict rolls back the whole unit (dossier+outbox); the next job
        // run retries safely.
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
}
