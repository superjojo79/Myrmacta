package com.flowforge.domain.workflow.model;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record WorkflowTypeConfig(
        UUID id,
        String tenantId,
        String workflowKey,
        String label,
        List<WorkflowStatusDefinition> statusDefinitions,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<String> observerUserIds
) {
    public WorkflowTypeConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(workflowKey, "workflowKey");
        Objects.requireNonNull(label, "label");
        statusDefinitions = List.copyOf(Objects.requireNonNull(statusDefinitions, "statusDefinitions"));
        allowedDossierStatuses =
                List.copyOf(Objects.requireNonNull(allowedDossierStatuses, "allowedDossierStatuses"));
        if (allowedDossierStatuses.isEmpty()) {
            throw new IllegalArgumentException("allowedDossierStatuses must not be empty");
        }
        long initials = statusDefinitions.stream().filter(WorkflowStatusDefinition::initial).count();
        if (initials != 1) {
            throw new IllegalArgumentException("statusDefinitions must contain exactly one initial status");
        }
        observerUserIds = normalizeObserverUserIds(observerUserIds);
    }

    private static List<String> normalizeObserverUserIds(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String id : raw) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("observerUserIds must not contain blank entries");
            }
            deduped.add(id);
        }
        return List.copyOf(new ArrayList<>(deduped));
    }

    public String initialStatusCode() {
        return statusDefinitions.stream()
                .filter(WorkflowStatusDefinition::initial)
                .map(WorkflowStatusDefinition::code)
                .findFirst()
                .orElseThrow();
    }

    public boolean allowsDossierStatus(DossierStatus status) {
        return allowedDossierStatuses.contains(status);
    }

    public boolean isKnownStatus(String code) {
        return statusDefinitions.stream().anyMatch(s -> s.code().equals(code));
    }

    public StatusOutcome outcomeOf(String code) {
        return definition(code).map(WorkflowStatusDefinition::outcome).orElse(null);
    }

    public Optional<WorkflowStatusDefinition> definition(String code) {
        return statusDefinitions.stream().filter(s -> s.code().equals(code)).findFirst();
    }
}
