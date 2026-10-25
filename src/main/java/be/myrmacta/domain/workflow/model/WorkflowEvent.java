package be.myrmacta.domain.workflow.model;

import java.time.Instant;
import java.util.UUID;

public sealed interface WorkflowEvent
        permits WorkflowStartedEvent, WorkflowCompletedEvent, WorkflowFailedEvent {

    UUID workflowInstanceId();

    Instant occurredAt();
}
