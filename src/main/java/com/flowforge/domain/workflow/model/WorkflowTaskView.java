package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

public record WorkflowTaskView(
        String taskId,
        String name,
        String assignee,
        String taskDefinitionKey,
        Instant createTime
) {
    public WorkflowTaskView {
        Objects.requireNonNull(taskId, "taskId");
    }
}
