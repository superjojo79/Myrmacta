package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierRepositoryAdapter implements DossierRepository {

    private final SpringDataDossierRepository repository;

    public JpaDossierRepositoryAdapter(SpringDataDossierRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(Dossier dossier) {
        repository.save(DossierJpaEntity.fromDomain(dossier));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dossier> findById(UUID id) {
        return repository.findById(id).map(DossierJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dossier> findByReference(String tenantId, String reference) {
        return repository.findByTenantIdAndReference(tenantId, reference).map(DossierJpaEntity::toDomain);
    }
}
