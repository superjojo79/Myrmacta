package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record DossierType(
        UUID id,
        String tenantId,
        String code,
        String label,
        String workflowKey,
        boolean active,
        boolean allowUnknownFields,
        List<FieldDefinition> fieldDefinitions
) {
    public DossierType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(workflowKey, "workflowKey");
        fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
    }

    public DossierType withUpdate(
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions) {
        return new DossierType(
                id,
                tenantId,
                code,
                label,
                workflowKey,
                active,
                allowUnknownFields,
                fieldDefinitions);
    }
}
