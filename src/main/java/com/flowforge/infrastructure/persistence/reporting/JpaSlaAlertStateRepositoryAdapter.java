package com.flowforge.infrastructure.persistence.reporting;

import com.flowforge.domain.reporting.model.SlaAlertState;
import com.flowforge.domain.reporting.port.out.SlaAlertStateRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSlaAlertStateRepositoryAdapter implements SlaAlertStateRepository {

    private final SpringDataSlaAlertStateRepository repository;

    public JpaSlaAlertStateRepositoryAdapter(SpringDataSlaAlertStateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void replaceAll(String tenantId, List<SlaAlertState> states) {
        repository.deleteByTenantId(tenantId);
        repository.flush();
        for (SlaAlertState state : states) {
            repository.save(SlaAlertStateJpaEntity.fromDomain(state));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SlaAlertState> find(String tenantId, UUID dossierId) {
        return repository.findByTenantIdAndDossierId(tenantId, dossierId).map(SlaAlertStateJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public boolean transition(SlaAlertState expectedPrevious, SlaAlertState next) {
        return repository.transition(
                        next.tenantId(), next.dossierId(), expectedPrevious.lastStatus(), next.lastStatus(), next.updatedAt())
                == 1;
    }

    // L'insertion s'exécute dans sa propre transaction (REQUIRES_NEW sur le dépôt) : la violation d'unicité est interceptée ici
    // sans marquer rollback-only la transaction de l'appelant, qui rend alors la main sans notifier.
    @Override
    public boolean insertIfAbsent(SlaAlertState state) {
        try {
            return repository.insertStrict(state.tenantId(), state.dossierId(), state.lastStatus(), state.updatedAt()) == 1;
        } catch (org.springframework.dao.DataIntegrityViolationException alreadyThere) {
            return false;
        }
    }

    @Override
    @Transactional
    public SlaAlertState save(SlaAlertState state) {
        return repository.saveAndFlush(SlaAlertStateJpaEntity.fromDomain(state)).toDomain();
    }
}
