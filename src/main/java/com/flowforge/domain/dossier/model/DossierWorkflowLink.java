package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DossierWorkflowLink(
        UUID id,
        UUID dossierId,
        String tenantId,
        String processInstanceId,
        String processDefinitionKey,
        int sequence,
        String status,
        String triggerRef,
        Instant startedAt,
        Instant endedAt,
        String endReason
) {
    public DossierWorkflowLink {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dossierId, "dossierId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(processDefinitionKey, "processDefinitionKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(triggerRef, "triggerRef");
        Objects.requireNonNull(startedAt, "startedAt");
    }

    public boolean isReserved() {
        return endedAt == null && processInstanceId == null;
    }

    public boolean isActive() {
        return endedAt == null && processInstanceId != null;
    }

    public boolean isTerminated() {
        return endedAt != null;
    }

    public DossierWorkflowLink withProcessInstance(String processInstanceId) {
        Objects.requireNonNull(processInstanceId, "processInstanceId");
        return new DossierWorkflowLink(
                id,
                dossierId,
                tenantId,
                processInstanceId,
                processDefinitionKey,
                sequence,
                status,
                triggerRef,
                startedAt,
                endedAt,
                endReason);
    }

    public DossierWorkflowLink abort(AbortReservedReason reason, Instant at) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(at, "at");
        return new DossierWorkflowLink(
                id,
                dossierId,
                tenantId,
                processInstanceId,
                processDefinitionKey,
                sequence,
                status,
                triggerRef,
                startedAt,
                at,
                reason.name());
    }

    public DossierWorkflowLink withStatus(String newStatus, Instant endedAt, String endReason) {
        Objects.requireNonNull(newStatus, "newStatus");
        return new DossierWorkflowLink(
                id,
                dossierId,
                tenantId,
                processInstanceId,
                processDefinitionKey,
                sequence,
                newStatus,
                triggerRef,
                startedAt,
                endedAt,
                endReason);
    }
}
