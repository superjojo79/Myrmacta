package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.WorkflowTypeConfig;

public interface SaveWorkflowTypeConfigUseCase {

    WorkflowTypeConfig create(WorkflowTypeConfig config);

    WorkflowTypeConfig update(WorkflowTypeConfig config);
}
