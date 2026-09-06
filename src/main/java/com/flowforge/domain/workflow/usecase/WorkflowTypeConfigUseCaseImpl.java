package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Objects;
import java.util.Optional;

/**
 * Correctif extension-mcp-flux-metier (D5) : {@code create}/{@code update} gagnent le garde-fou
 * domaine déjà en place pour {@code DossierType} — écart pré-existant depuis {@code deploy_workflow}
 * (PR #82), qui appelait ce use case sans aucun contrôle de rôle. {@code getByKey}/{@code list}
 * restent volontairement ouverts (aucun garde), pour ne pas régresser {@code list_workflow_types}.
 */
public final class WorkflowTypeConfigUseCaseImpl
        implements SaveWorkflowTypeConfigUseCase, GetWorkflowTypeConfigUseCase {

    private final String tenantId;
    private final WorkflowTypeConfigRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public WorkflowTypeConfigUseCaseImpl(
            String tenantId, WorkflowTypeConfigRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public WorkflowTypeConfig create(WorkflowTypeConfig config, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.save(config);
    }

    @Override
    public WorkflowTypeConfig update(WorkflowTypeConfig config, AccessContext.User actor) {
        tenantAdminGuard.requireTenantOrWorkflowAdminOrApiService(actor);
        return repository.save(config);
    }

    @Override
    public Optional<WorkflowTypeConfig> getByKey(String workflowKey) {
        return repository.findByWorkflowKey(tenantId, workflowKey);
    }

    @Override
    public PageResult<WorkflowTypeConfig> list(PageQuery query) {
        return repository.page(tenantId, query);
    }
}
