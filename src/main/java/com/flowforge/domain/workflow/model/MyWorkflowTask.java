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
        Double slaRemainingHours,
        String formKey) {

    public MyWorkflowTask {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(assigneeId, "assigneeId");
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(dossierReference, "dossierReference");
        Objects.requireNonNull(linkId, "linkId");
    }

    /** Convenience for callers built before {@code formKey} existed (TODO §52) — defaults to no form. */
    public MyWorkflowTask(
            String taskId,
            String name,
            String assigneeId,
            Instant createdAt,
            String processInstanceId,
            UUID dossierId,
            String dossierReference,
            UUID linkId,
            Double slaRemainingHours) {
        this(taskId, name, assigneeId, createdAt, processInstanceId, dossierId, dossierReference, linkId,
                slaRemainingHours, null);
    }
}
