package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierElementRepositoryAdapter implements DossierElementRepository {

    private final SpringDataDossierElementRepository repository;

    public JpaDossierElementRepositoryAdapter(SpringDataDossierElementRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public DossierElement save(DossierElement element) {
        return repository.save(DossierElementJpaEntity.fromDomain(element)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierElement> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(DossierElementJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierElement> findByDossierIdOrderByAddedAtAsc(UUID dossierId) {
        return repository.findByDossierIdOrderByAddedAtAsc(dossierId).stream()
                .map(DossierElementJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByDossierId(UUID dossierId) {
        repository.deleteByDossierId(dossierId);
    }
}
