package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaTenantSlaSettingsRepositoryAdapter implements TenantSlaSettingsRepository {

    private final SpringDataTenantSlaSettingsRepository repository;
    private final SpringDataTenantSlaAlertWebhookRepository webhookRepository;

    public JpaTenantSlaSettingsRepositoryAdapter(
            SpringDataTenantSlaSettingsRepository repository,
            SpringDataTenantSlaAlertWebhookRepository webhookRepository) {
        this.repository = repository;
        this.webhookRepository = webhookRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TenantSlaSettings> findByTenantId(String tenantId) {
        return repository.findById(tenantId).map(entity -> entity.toDomain(webhookIds(tenantId)));
    }

    @Override
    @Transactional
    public TenantSlaSettings save(TenantSlaSettings settings) {
        TenantSlaSettingsJpaEntity saved = repository.saveAndFlush(TenantSlaSettingsJpaEntity.fromDomain(settings));
        webhookRepository.deleteByTenantId(settings.tenantId());
        webhookRepository.flush();
        for (UUID webhookId : settings.alertWebhookIds()) {
            webhookRepository.save(new TenantSlaAlertWebhookJpaEntity(settings.tenantId(), webhookId));
        }
        return saved.toDomain(settings.alertWebhookIds());
    }

    private List<UUID> webhookIds(String tenantId) {
        return webhookRepository.findByTenantId(tenantId).stream()
                .map(TenantSlaAlertWebhookJpaEntity::webhookId)
                .toList();
    }
}
