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
        List<FieldDefinition> fieldDefinitions,
        Integer retentionDays,
        boolean retentionNever
) {
    public DossierType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(workflowKey, "workflowKey");
        fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
        validateRetention(retentionDays, retentionNever);
    }

    public static void validateRetention(Integer retentionDays, boolean retentionNever) {
        if (retentionNever && retentionDays != null) {
            throw new InvalidRetentionPolicyException(
                    "retentionDays must be null when retentionNever is true");
        }
        if (!retentionNever && retentionDays != null && retentionDays <= 0) {
            throw new InvalidRetentionPolicyException("retentionDays must be > 0 when set");
        }
    }

    /** Convenience for call sites without retention override (inherit). */
    public static DossierType inherit(
            UUID id,
            String tenantId,
            String code,
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
                fieldDefinitions,
                null,
                false);
    }

    public DossierType withUpdate(
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever) {
        return new DossierType(
                id,
                tenantId,
                code,
                label,
                workflowKey,
                active,
                allowUnknownFields,
                fieldDefinitions,
                retentionDays,
                retentionNever);
    }

    /** Metadata only — true if any field is marked sensitive (no redaction yet). */
    public boolean hasSensitiveFields() {
        return fieldDefinitions.stream().anyMatch(FieldDefinition::isSensitive);
    }
}
