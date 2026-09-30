package com.flowforge.domain.dossier.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

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
        boolean retentionNever,
        Integer slaTargetHours,
        List<String> documentTemplateKeys,
        String createdBy,
        String updatedBy
) {
    // Deliberately restrictive: `code` is embedded verbatim in purge_outbox.payload_json
    // (PurgeOutboxPayloads) and used as a URL path segment (DossierTypeController) — this charset
    // is safe in both contexts without further escaping, and matches every code value already in
    // use (e.g. CLAIM, LEGAL, LOG).
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    public DossierType {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(workflowKey, "workflowKey");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedBy, "updatedBy");
        if (workflowKey.isBlank()) {
            throw new IllegalArgumentException("workflowKey must not be blank");
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "code must match " + CODE_PATTERN.pattern() + ", was: " + code);
        }
        fieldDefinitions = fieldDefinitions == null ? List.of() : List.copyOf(fieldDefinitions);
        documentTemplateKeys = documentTemplateKeys == null ? List.of() : List.copyOf(documentTemplateKeys);
        requireUniqueFieldNames(fieldDefinitions);
        validateRetention(retentionDays, retentionNever);
        validateSlaTargetHours(slaTargetHours);
    }

    /**
     * Convenience for callers built before {@code documentTemplateKeys} existed (TODO §52) —
     * defaults it to {@code List.of()} (D-FORM-DOCGEN-TEMPLATE-SCOPE: empty = no template
     * available for the {@code document-generation} task form kind, same semantics as a
     * freshly-created type with no association configured yet).
     */
    public DossierType(
            UUID id,
            String tenantId,
            String code,
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever,
            Integer slaTargetHours,
            String createdBy,
            String updatedBy) {
        this(id, tenantId, code, label, workflowKey, active, allowUnknownFields, fieldDefinitions,
                retentionDays, retentionNever, slaTargetHours, List.of(), createdBy, updatedBy);
    }

    // D-NAME-UNIQUE — structural invariant of the schema itself, not a per-dossier value check
    // (that's DossierFieldValidator's job), so it belongs here alongside the `code` validation.
    // Covers all 3 write paths (REST create, REST update, MCP define_dossier_type) for free since
    // they all construct a DossierType.
    private static void requireUniqueFieldNames(List<FieldDefinition> fieldDefinitions) {
        Set<String> seen = new HashSet<>();
        Set<String> columns = new HashSet<>();
        for (FieldDefinition def : fieldDefinitions) {
            if (!seen.add(def.name())) {
                throw new IllegalArgumentException("Duplicate fieldDefinition name: " + def.name());
            }
            // TODO §76 : un montant ou une référence devient plusieurs colonnes CSV (`m.amount`…) ; un champ nommé
            // « m.amount » à côté d'un montant « m » donnerait deux colonnes identiques.
            for (String column : com.flowforge.domain.dossier.DossierFieldCsv.headers(def)) {
                if (!columns.add(column)) {
                    throw new IllegalArgumentException("CSV column name collision: " + column);
                }
            }
        }
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

    public static void validateSlaTargetHours(Integer slaTargetHours) {
        if (slaTargetHours != null && slaTargetHours <= 0) {
            throw new IllegalArgumentException("slaTargetHours must be > 0 when set");
        }
    }

    /** Convenience for call sites without retention override (inherit) or author (defaults to "system"). */
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
                false,
                null,
                "system",
                "system");
    }

    public DossierType withUpdate(
            String label,
            String workflowKey,
            boolean active,
            boolean allowUnknownFields,
            List<FieldDefinition> fieldDefinitions,
            Integer retentionDays,
            boolean retentionNever,
            Integer slaTargetHours,
            List<String> documentTemplateKeys,
            String updatedBy) {
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
                retentionNever,
                slaTargetHours,
                documentTemplateKeys,
                createdBy,
                updatedBy);
    }

    /** Metadata only — true if any field is marked sensitive (no redaction yet). */
    public boolean hasSensitiveFields() {
        return fieldDefinitions.stream().anyMatch(FieldDefinition::isSensitive);
    }
}
