package com.flowforge.infrastructure.persistence.workflow;

import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaTaskQuestionSchemaRepositoryAdapter implements TaskQuestionSchemaRepository {

    private final SpringDataTaskQuestionSchemaRepository repository;

    JpaTaskQuestionSchemaRepositoryAdapter(SpringDataTaskQuestionSchemaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public TaskQuestionSchema save(TaskQuestionSchema schema) {
        return repository.saveAndFlush(TaskQuestionSchemaJpaEntity.fromDomain(schema)).toDomain();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TaskQuestionSchema> findByKey(String tenantId, String schemaKey) {
        return repository.findByTenantIdAndSchemaKey(tenantId, schemaKey).map(TaskQuestionSchemaJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskQuestionSchema> findAll(String tenantId) {
        return repository.findByTenantIdOrderBySchemaKeyAsc(tenantId).stream()
                .map(TaskQuestionSchemaJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void delete(String tenantId, String schemaKey) {
        repository.deleteByTenantIdAndSchemaKey(tenantId, schemaKey);
    }
}
