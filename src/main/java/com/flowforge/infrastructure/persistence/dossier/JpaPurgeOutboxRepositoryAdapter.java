package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
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
        // D-OUTBOX-UNIQ: a concurrent job run may have already inserted this dossier's row —
        // the UNIQUE(dossier_id) constraint makes the conflict an idempotent no-op.
        try {
            repository.saveAndFlush(PurgeOutboxJpaEntity.fromDomain(record));
        } catch (DataIntegrityViolationException ex) {
            // already enqueued by another run — swallow.
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByDossierId(UUID dossierId) {
        return repository.existsByDossierId(dossierId);
    }
}
