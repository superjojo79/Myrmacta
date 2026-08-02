package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowFailedEvent(UUID workflowInstanceId, Instant occurredAt, String reason)
        implements WorkflowEvent {

    public WorkflowFailedEvent {
        Objects.requireNonNull(workflowInstanceId, "workflowInstanceId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(reason, "reason");
    }
}
