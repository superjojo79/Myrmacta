package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * TODO §113 (D-MAILROOM-REPLY-WORKFLOW) et §122 — workflow de réponse par type de dossier, en JPA. Un réglage par type au
 * plus (clé primaire). {@link #save} remplace : la dernière écriture l'emporte.
 */
@Component
class JpaReplyWorkflowSettingsRepositoryAdapter implements ReplyWorkflowSettings {

    private final SpringDataDossierTypeReplyWorkflowRepository repository;

    JpaReplyWorkflowSettingsRepositoryAdapter(SpringDataDossierTypeReplyWorkflowRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReplyWorkflowSetting> find(String tenantId, String dossierTypeCode) {
        return repository.findById(new DossierTypeReplyWorkflowJpaEntity.Key(tenantId, dossierTypeCode))
                .map(DossierTypeReplyWorkflowJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(ReplyWorkflowSetting setting) {
        DossierTypeReplyWorkflowJpaEntity entity = repository
                .findById(new DossierTypeReplyWorkflowJpaEntity.Key(setting.tenantId(), setting.dossierTypeCode()))
                .orElseGet(() -> DossierTypeReplyWorkflowJpaEntity.fromDomain(setting));
        entity.applyDomain(setting);
        repository.save(entity);
    }

    @Override
    @Transactional
    public void delete(String tenantId, String dossierTypeCode) {
        repository.deleteOfType(tenantId, dossierTypeCode);
    }
}
