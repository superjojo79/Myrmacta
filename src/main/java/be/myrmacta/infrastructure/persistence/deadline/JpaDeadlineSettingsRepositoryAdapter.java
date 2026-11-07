package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §115 (D-DEADLINE-FIELD) et §122 — réglage d'échéance par type de dossier, en JPA. Un réglage par type au plus
 * (clé primaire). {@link #save} remplace : la dernière écriture l'emporte.
 */
@Component
class JpaDeadlineSettingsRepositoryAdapter implements DeadlineSettings {

    private final SpringDataDossierTypeDeadlineRepository repository;

    JpaDeadlineSettingsRepositoryAdapter(SpringDataDossierTypeDeadlineRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DeadlineSetting> find(String tenantId, String dossierTypeCode) {
        return repository.findById(new DossierTypeDeadlineJpaEntity.Key(tenantId, dossierTypeCode))
                .map(DossierTypeDeadlineJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeadlineSetting> findAll(String tenantId) {
        return repository.findByTenantIdOrderByDossierTypeCodeAsc(tenantId).stream()
                .map(DossierTypeDeadlineJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void save(DeadlineSetting setting) {
        DossierTypeDeadlineJpaEntity entity = repository
                .findById(new DossierTypeDeadlineJpaEntity.Key(setting.tenantId(), setting.dossierTypeCode()))
                .orElseGet(() -> DossierTypeDeadlineJpaEntity.fromDomain(setting));
        entity.applyDomain(setting);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String dossierTypeCode) {
        repository.deleteOfType(tenantId, dossierTypeCode);
    }
}
