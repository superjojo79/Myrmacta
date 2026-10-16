package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.user.model.KnownUser;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record StartWorkflowRequest(String processDefinitionKey, Map<String, Object> variables) {}

record CompleteTaskRequest(Map<String, Object> variables) {}

record ReassignWorkflowTaskRequest(String assigneeId) {}

/** D-ASSIGN-DIR — rôles volontairement absents : seul le choix d'un assigné est servi. */
record AssignableUserResponse(String userId, String displayName, String email) {

    static AssignableUserResponse from(KnownUser user) {
        return new AssignableUserResponse(user.userId(), user.displayName(), user.email());
    }
}

record WorkflowLinkResponse(
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
        String endReason,
        String startedBy) {

    static WorkflowLinkResponse from(DossierWorkflowLink link) {
        return new WorkflowLinkResponse(
                link.id(),
                link.dossierId(),
                link.tenantId(),
                link.processInstanceId(),
                link.processDefinitionKey(),
                link.sequence(),
                link.status(),
                link.triggerRef(),
                link.startedAt(),
                link.endedAt(),
                link.endReason(),
                link.startedBy());
    }
}

record WorkflowTypeConfigRequest(
        String workflowKey,
        String label,
        List<WorkflowStatusDefinitionRequest> statusDefinitions,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<String> observerUserIds) {

    WorkflowTypeConfig toDomain(String tenantId, UUID id, String keyOverride, String createdBy, String updatedBy) {
        String key = keyOverride == null ? workflowKey : keyOverride;
        return new WorkflowTypeConfig(
                id,
                tenantId,
                required(key, "workflowKey"),
                required(label, "label"),
                statusDefinitions == null
                        ? List.of()
                        : statusDefinitions.stream().map(WorkflowStatusDefinitionRequest::toDomain).toList(),
                allowedDossierStatuses == null ? List.of() : allowedDossierStatuses,
                onStartDossierStatus,
                observerUserIds,
                createdBy,
                updatedBy);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}

record WorkflowStatusDefinitionRequest(String code, String label, boolean initial, StatusOutcome outcome) {
    WorkflowStatusDefinition toDomain() {
        return new WorkflowStatusDefinition(code, label, initial, outcome);
    }
}

record WorkflowTypeConfigResponse(
        UUID id,
        String tenantId,
        String workflowKey,
        String label,
        List<WorkflowStatusDefinition> statusDefinitions,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<String> observerUserIds) {

    static WorkflowTypeConfigResponse from(WorkflowTypeConfig config) {
        return new WorkflowTypeConfigResponse(
                config.id(),
                config.tenantId(),
                config.workflowKey(),
                config.label(),
                config.statusDefinitions(),
                config.allowedDossierStatuses(),
                config.onStartDossierStatus(),
                config.observerUserIds());
    }
}
