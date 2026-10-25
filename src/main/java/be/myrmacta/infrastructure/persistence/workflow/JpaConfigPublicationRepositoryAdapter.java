package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.model.ConfigPublication;
import be.myrmacta.domain.workflow.model.DraftArtifactType;
import be.myrmacta.domain.workflow.port.out.ConfigPublicationRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaConfigPublicationRepositoryAdapter implements ConfigPublicationRepository {

    private final SpringDataConfigPublicationRepository repository;

    JpaConfigPublicationRepositoryAdapter(SpringDataConfigPublicationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public ConfigPublication save(ConfigPublication publication) {
        return repository.saveAndFlush(ConfigPublicationJpaEntity.fromDomain(publication)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigPublication> findRecent(String tenantId, int limit) {
        return repository.findByTenantIdOrderByPublishedAtDesc(tenantId, PageRequest.of(0, limit)).stream()
                .map(ConfigPublicationJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConfigPublication> findByArtifact(String tenantId, DraftArtifactType type, String key, int limit) {
        return repository
                .findByTenantIdAndArtifactTypeAndArtifactKeyOrderByPublishedAtDesc(
                        tenantId, type.name(), key, PageRequest.of(0, limit))
                .stream()
                .map(ConfigPublicationJpaEntity::toDomain)
                .toList();
    }
}
