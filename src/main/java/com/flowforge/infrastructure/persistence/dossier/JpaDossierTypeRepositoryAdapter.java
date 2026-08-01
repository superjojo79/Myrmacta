package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierTypeRepositoryAdapter implements DossierTypeRepository {

    private final SpringDataDossierTypeRepository repository;

    public JpaDossierTypeRepositoryAdapter(SpringDataDossierTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(DossierType type) {
        repository.save(DossierTypeJpaEntity.fromDomain(type));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierType> findById(UUID id) {
        return repository.findById(id).map(DossierTypeJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DossierType> findByCode(String tenantId, String code) {
        return repository.findByTenantIdAndCode(tenantId, code).map(DossierTypeJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DossierType> findAll(String tenantId) {
        return repository.findAllByTenantIdOrderByCode(tenantId).stream()
                .map(DossierTypeJpaEntity::toDomain)
                .toList();
    }
}
