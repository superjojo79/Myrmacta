package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;

public interface StartWorkflowUseCase {

    WorkflowInstance start(StartWorkflowCommand cmd);
}
