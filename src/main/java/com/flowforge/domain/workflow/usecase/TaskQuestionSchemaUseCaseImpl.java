package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
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
 * RBAC (`TENANT_ADMIN`/`WORKFLOW_ADMIN`) is enforced both at the controller via
 * {@code @PreAuthorize} and here via {@code TenantAdminGuard} (extension-mcp-flux-metier, D5) —
 * an MCP caller invoking this use case directly (bypassing the REST controller, same pattern as
 * {@code DefineDossierTypeTool}) would otherwise skip the controller-only check entirely, on
 * every method including reads (unlike {@code WorkflowTypeConfigUseCaseImpl}, whose reads stay
 * open).
 */
public final class TaskQuestionSchemaUseCaseImpl
        implements SaveTaskQuestionSchemaUseCase, GetTaskQuestionSchemaUseCase, DeleteTaskQuestionSchemaUseCase {

    private final String tenantId;
    private final TaskQuestionSchemaRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public TaskQuestionSchemaUseCaseImpl(
            String tenantId, TaskQuestionSchemaRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public TaskQuestionSchema save(TaskQuestionSchema schema, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.save(schema);
    }

    @Override
    public Optional<TaskQuestionSchema> getByKey(String schemaKey, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.findByKey(tenantId, schemaKey);
    }

    @Override
    public PageResult<TaskQuestionSchema> list(PageQuery query, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.page(tenantId, query);
    }

    @Override
    public void delete(String schemaKey, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        repository.delete(tenantId, schemaKey);
    }
}
