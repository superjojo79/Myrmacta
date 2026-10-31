package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record WorkflowTaskView(
        String taskId,
        String name,
        String assignee,
        String taskDefinitionKey,
        Instant createTime,
        String formKey,
        /** Groupes candidats de la tâche (TODO §113), triés ; vide si aucun. */
        List<String> candidateGroups
) {
    public WorkflowTaskView {
        Objects.requireNonNull(taskId, "taskId");
        candidateGroups = candidateGroups == null ? List.of() : List.copyOf(candidateGroups);
    }

    /** Convenience for callers built before {@code candidateGroups} existed (TODO §113). */
    public WorkflowTaskView(String taskId, String name, String assignee, String taskDefinitionKey, Instant createTime, String formKey) {
        this(taskId, name, assignee, taskDefinitionKey, createTime, formKey, List.of());
    }

    /** Convenience for callers built before {@code formKey} existed (TODO §52) — defaults to no form. */
    public WorkflowTaskView(String taskId, String name, String assignee, String taskDefinitionKey, Instant createTime) {
        this(taskId, name, assignee, taskDefinitionKey, createTime, null, List.of());
    }
}
