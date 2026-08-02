package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.WorkflowVariables;
import java.util.Map;
import java.util.Objects;

public final class CompleteTaskUseCaseImpl implements CompleteTaskUseCase {

    private final WorkflowEnginePort workflowEnginePort;

    public CompleteTaskUseCaseImpl(WorkflowEnginePort workflowEnginePort) {
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
    }

    @Override
    public void complete(String taskId, Map<String, Object> variables) {
        WorkflowVariables.assertNoReservedKeys(variables);
        workflowEnginePort.completeTask(taskId, variables == null ? Map.of() : variables);
    }
}
