package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** TODO §114 (D-GROUP-LINK) et §122 — groupe destinataire porté par un lien de workflow, en JPA. */
@Component
class JpaWorkflowLinkGroupRepositoryAdapter implements WorkflowLinkGroupRepository {

    private final SpringDataWorkflowLinkGroupRepository repository;

    JpaWorkflowLinkGroupRepositoryAdapter(SpringDataWorkflowLinkGroupRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void assign(String tenantId, UUID linkId, String groupCode) {
        WorkflowLinkGroupJpaEntity entity = repository.findById(linkId).orElseGet(() -> WorkflowLinkGroupJpaEntity.of(tenantId, linkId, groupCode));
        entity.assign(groupCode);
        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findGroup(UUID linkId) {
        return repository.findById(linkId).map(WorkflowLinkGroupJpaEntity::groupCode);
    }
}
