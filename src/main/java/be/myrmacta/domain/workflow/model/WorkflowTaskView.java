package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;

public record WorkflowTaskView(
        String taskId,
        String name,
        String assignee,
        String taskDefinitionKey,
        Instant createTime,
        String formKey
) {
    public WorkflowTaskView {
        Objects.requireNonNull(taskId, "taskId");
    }

    /** Convenience for callers built before {@code formKey} existed (TODO §52) — defaults to no form. */
    public WorkflowTaskView(String taskId, String name, String assignee, String taskDefinitionKey, Instant createTime) {
        this(taskId, name, assignee, taskDefinitionKey, createTime, null);
    }
}
