package com.flowforge.domain.workflow.model;

import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.workflow.WorkflowVariables;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Admin-managed schema for the {@code user-question} task form kind (TODO §55) — a mini-form
 * (one or more {@link FieldDefinition}s) a workflow designer attaches to a {@code userTask} via
 * {@code formKey = "user-question:<schemaKey>"}, independent of any {@code Dossier}. Reuses
 * {@link FieldDefinition}/{@link FieldType} as-is (D-USERQ-FIELD-REUSE) rather than duplicating
 * the same shape in this bounded context.
 */
public record TaskQuestionSchema(
        UUID id,
        String tenantId,
        String schemaKey,
        String label,
        List<FieldDefinition> fields,
        String createdBy,
        String updatedBy
) {
    // Embedded verbatim after "user-question:" in formKey (TaskQuestionFormKeys) — restrictive
    // charset avoids ambiguity in that compound string, same rationale as DossierType.CODE_PATTERN.
    private static final Pattern SCHEMA_KEY_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    public TaskQuestionSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(schemaKey, "schemaKey");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(createdBy, "createdBy");
        Objects.requireNonNull(updatedBy, "updatedBy");
        if (!SCHEMA_KEY_PATTERN.matcher(schemaKey).matches()) {
            throw new IllegalArgumentException(
                    "schemaKey must match " + SCHEMA_KEY_PATTERN.pattern() + ", was: " + schemaKey);
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("fields must not be empty");
        }
        requireUniqueFieldNames(fields);
        requireNoFileFields(fields);
        requireNoReservedOrKnownFieldNames(fields);
    }

    private static void requireUniqueFieldNames(List<FieldDefinition> fields) {
        Set<String> seen = new HashSet<>();
        for (FieldDefinition field : fields) {
            if (!seen.add(field.name())) {
                throw new IllegalArgumentException("Duplicate fieldDefinition name: " + field.name());
            }
        }
    }

    // D-USERQ-SCHEMA-CONCURRENCY note n/a here; this is a distinct rule — FILE has no upload
    // mechanism independent of a Dossier (non-goal, requirements doc), so it would be silently
    // non-functional if allowed through.
    private static void requireNoFileFields(List<FieldDefinition> fields) {
        for (FieldDefinition field : fields) {
            if (field.type() == FieldType.FILE) {
                throw new IllegalArgumentException(
                        "FieldType.FILE is not supported by TaskQuestionSchema (field: " + field.name() + ")");
            }
            // TODO §76 (D-CHAMPS-USERQ) : les types structurés sont réservés aux champs de dossier en D1 — le formulaire de
            // tâche et le portail ont leur propre rendu, et les réponses sont rangées en variables sans normalisation.
            if (field.type() == FieldType.MONEY
                    || field.type() == FieldType.REPEATABLE_GROUP
                    || field.type() == FieldType.EXTERNAL_REF) {
                throw new IllegalArgumentException("FieldType." + field.type()
                        + " is not supported by TaskQuestionSchema yet (field: " + field.name() + ")");
            }
            // TODO §80 (D1, règles) : calcul, visibilité et obligation conditionnelle s'évaluent sur les champs du dossier, à
            // l'enregistrement du dossier — pas sur les réponses d'une question de tâche.
            if (field.formula() != null || field.visibleWhen() != null || field.requiredWhen() != null) {
                throw new IllegalArgumentException("field rules (formula, visibleWhen, requiredWhen) are not supported by "
                        + "TaskQuestionSchema (field: " + field.name() + ")");
            }
        }
    }

    // D-USERQ-VAR-COLLISION: rejected at save time, before any BPMN deployment references this
    // schema — see WorkflowVariables.isReservedOrKnownKey for the checked name set.
    private static void requireNoReservedOrKnownFieldNames(List<FieldDefinition> fields) {
        for (FieldDefinition field : fields) {
            if (WorkflowVariables.isReservedOrKnownKey(field.name())) {
                throw new IllegalArgumentException(
                        "Field name '" + field.name()
                                + "' collides with a reserved or already-used workflow variable name");
            }
        }
    }

    public TaskQuestionSchema withUpdate(String label, List<FieldDefinition> fields, String updatedBy) {
        return new TaskQuestionSchema(id, tenantId, schemaKey, label, fields, createdBy, updatedBy);
    }
}
