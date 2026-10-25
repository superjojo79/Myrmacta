package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Instant;
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

    @Override
    @Transactional
    public void advanceLastAlertScanAt(String tenantId, Instant scannedAt) {
        repository.advanceLastAlertScanAt(tenantId, scannedAt);
    }

    @Override
    @Transactional
    public boolean advanceLastBiExportAt(String tenantId, Instant expected, Instant next) {
        return (expected == null
                        ? repository.advanceFirstBiExport(tenantId, next)
                        : repository.advanceLastBiExportAt(tenantId, expected, next))
                == 1;
    }

    private List<UUID> webhookIds(String tenantId) {
        return webhookRepository.findByTenantId(tenantId).stream()
                .map(TenantSlaAlertWebhookJpaEntity::webhookId)
                .toList();
    }
}
