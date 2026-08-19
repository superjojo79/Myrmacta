package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Objects;
import java.util.Optional;

public final class WorkflowTypeConfigUseCaseImpl
        implements SaveWorkflowTypeConfigUseCase, GetWorkflowTypeConfigUseCase {

    private final String tenantId;
    private final WorkflowTypeConfigRepository repository;

    public WorkflowTypeConfigUseCaseImpl(String tenantId, WorkflowTypeConfigRepository repository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public WorkflowTypeConfig create(WorkflowTypeConfig config) {
        return repository.save(config);
    }

    @Override
    public WorkflowTypeConfig update(WorkflowTypeConfig config) {
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
