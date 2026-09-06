package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.port.in.DeleteTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import java.util.Objects;
import java.util.Optional;

/**
 * RBAC (`TENANT_ADMIN`/`WORKFLOW_ADMIN`) is enforced entirely at the controller via
 * {@code @PreAuthorize} — same pattern as {@code WorkflowTypeConfigUseCaseImpl}, no domain guard
 * needed here (this is workflow-design-time admin config, not a `Dossier`-scoped entity).
 */
public final class TaskQuestionSchemaUseCaseImpl
        implements SaveTaskQuestionSchemaUseCase, GetTaskQuestionSchemaUseCase, DeleteTaskQuestionSchemaUseCase {

    private final String tenantId;
    private final TaskQuestionSchemaRepository repository;

    public TaskQuestionSchemaUseCaseImpl(String tenantId, TaskQuestionSchemaRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public TaskQuestionSchema save(TaskQuestionSchema schema) {
        return repository.save(schema);
    }

    @Override
    public Optional<TaskQuestionSchema> getByKey(String schemaKey) {
        return repository.findByKey(tenantId, schemaKey);
    }

    @Override
    public PageResult<TaskQuestionSchema> list(PageQuery query) {
        return repository.page(tenantId, query);
    }

    @Override
    public void delete(String schemaKey) {
        repository.delete(tenantId, schemaKey);
    }
}
