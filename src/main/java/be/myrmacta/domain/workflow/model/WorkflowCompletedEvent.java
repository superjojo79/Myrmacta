package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowCompletedEvent(UUID workflowInstanceId, Instant occurredAt) implements WorkflowEvent {

    public WorkflowCompletedEvent {
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
