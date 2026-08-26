package com.flowforge.domain.workflow.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MyWorkflowTask(
        String taskId,
        String name,
        String assigneeId,
        Instant createdAt,
        String processInstanceId,
        UUID dossierId,
        String dossierReference,
        UUID linkId,
        Double slaRemainingHours) {

    public MyWorkflowTask {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(assigneeId, "assigneeId");
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(dossierReference, "dossierReference");
        Objects.requireNonNull(linkId, "linkId");
    }
}
