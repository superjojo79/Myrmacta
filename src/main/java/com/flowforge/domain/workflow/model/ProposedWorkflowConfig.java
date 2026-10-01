package com.flowforge.domain.workflow.model;

import com.flowforge.domain.dossier.model.DossierStatus;
import java.util.List;
import java.util.Objects;

/**
 * TODO §82 (D6) — configuration de workflow proposée avec un brouillon BPMN venu du MCP ; appliquée (création ou mise à jour de
 * la {@link WorkflowTypeConfig}) seulement à la publication, jamais avant.
 */
public record ProposedWorkflowConfig(
        String label,
        List<WorkflowStatusDefinition> statusDefinitions,
        List<DossierStatus> allowedDossierStatuses,
        DossierStatus onStartDossierStatus,
        List<String> observerUserIds) {

    public ProposedWorkflowConfig {
        Objects.requireNonNull(label, "label");
        statusDefinitions = List.copyOf(Objects.requireNonNull(statusDefinitions, "statusDefinitions"));
        allowedDossierStatuses = List.copyOf(Objects.requireNonNull(allowedDossierStatuses, "allowedDossierStatuses"));
        if (allowedDossierStatuses.isEmpty()) {
            throw new IllegalArgumentException("allowedDossierStatuses must not be empty");
        }
        observerUserIds = observerUserIds == null ? List.of() : List.copyOf(observerUserIds);
    }
}
