package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTenantRetentionSettingsRepositoryAdapter implements TenantRetentionSettingsRepository {

    private final SpringDataTenantRetentionSettingsRepository repository;

    public JpaTenantRetentionSettingsRepositoryAdapter(SpringDataTenantRetentionSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantRetentionSettings> findByTenantId(String tenantId) {
        return repository.findById(tenantId).map(TenantRetentionSettingsJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public TenantRetentionSettings save(TenantRetentionSettings settings) {
        return repository
                .saveAndFlush(TenantRetentionSettingsJpaEntity.fromDomain(settings))
                .toDomain();
    }
}
