package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfig;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.persistence.SpringPages;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaWorkflowTypeConfigRepositoryAdapter implements WorkflowTypeConfigRepository {

    private final SpringDataWorkflowTypeConfigRepository repository;

    JpaWorkflowTypeConfigRepositoryAdapter(SpringDataWorkflowTypeConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public WorkflowTypeConfig save(WorkflowTypeConfig config) {
        return repository.saveAndFlush(WorkflowTypeConfigJpaEntity.fromDomain(config)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkflowTypeConfig> findByWorkflowKey(String tenantId, String workflowKey) {
        return repository.findByTenantIdAndWorkflowKey(tenantId, workflowKey)
                .map(WorkflowTypeConfigJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkflowTypeConfig> findAll(String tenantId) {
        return repository.findByTenantIdOrderByWorkflowKeyAsc(tenantId).stream()
                .map(WorkflowTypeConfigJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<WorkflowTypeConfig> page(String tenantId, PageQuery query) {
        return SpringPages.map(
                repository.findByTenantIdOrderByWorkflowKeyAsc(tenantId, SpringPages.of(query)),
                WorkflowTypeConfigJpaEntity::toDomain);
    }
}
