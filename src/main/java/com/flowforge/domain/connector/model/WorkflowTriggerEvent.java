package com.flowforge.domain.connector.model;

import java.util.Map;
import java.util.Objects;

public record WorkflowTriggerEvent(
        String processKey,
        String dossierTypeCode,
        Map<String, Object> dossierFields,
        Map<String, Object> variables,
        String existingDossierReference
) {
    public WorkflowTriggerEvent {
        Objects.requireNonNull(processKey, "processKey");
        dossierFields = dossierFields == null ? Map.of() : Map.copyOf(dossierFields);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }
}
