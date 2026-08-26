package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTenantSlaSettingsRepositoryAdapter implements TenantSlaSettingsRepository {

    private final SpringDataTenantSlaSettingsRepository repository;

    public JpaTenantSlaSettingsRepositoryAdapter(SpringDataTenantSlaSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantSlaSettings> findByTenantId(String tenantId) {
        return repository.findById(tenantId).map(TenantSlaSettingsJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public TenantSlaSettings save(TenantSlaSettings settings) {
        return repository.saveAndFlush(TenantSlaSettingsJpaEntity.fromDomain(settings)).toDomain();
    }
}
