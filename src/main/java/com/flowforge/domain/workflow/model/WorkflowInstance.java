package com.flowforge.domain.workflow.model;

import java.util.Objects;
import java.util.UUID;

public record WorkflowInstance(
        UUID id,
        String processKey,
        String tenantId,
        WorkflowStatus status
) {

    public WorkflowInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(processKey, "processKey");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(status, "status");
    }

    public static WorkflowInstance create(StartWorkflowCommand cmd) {
        Objects.requireNonNull(cmd, "cmd");
        return new WorkflowInstance(
                UUID.randomUUID(),
                cmd.processKey(),
                cmd.tenantId(),
                WorkflowStatus.RUNNING);
    }

    public WorkflowInstance complete() {
        if (status != WorkflowStatus.RUNNING) {
            throw new InvalidStateException("Workflow non actif");
        }
        return new WorkflowInstance(id, processKey, tenantId, WorkflowStatus.COMPLETED);
    }
}
