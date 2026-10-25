package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowStartedEvent(UUID workflowInstanceId, Instant occurredAt, String processKey)
        implements WorkflowEvent {

    public WorkflowStartedEvent {
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(processKey, "processKey");
        if (processKey.isBlank()) {
            throw new IllegalArgumentException("processKey must not be blank");
        }
    }
}
