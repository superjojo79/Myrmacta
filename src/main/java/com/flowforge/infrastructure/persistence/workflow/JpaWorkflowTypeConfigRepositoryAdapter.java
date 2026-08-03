package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
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
}
