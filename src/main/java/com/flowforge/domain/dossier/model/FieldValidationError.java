package com.flowforge.domain.dossier.model;

import java.util.Objects;

/**
 * Erreur de validation d'un champ. {@code reference} : référence d'un autre dossier en cause, renseignée seulement pour
 * {@code UNIQUE} (TODO §102, D-UNIQUE-HTTP) ; le message ne recopie jamais la valeur saisie.
 */
public record FieldValidationError(String field, String code, String message, String reference) {
    public FieldValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
    }

    public FieldValidationError(String field, String code, String message) {
        this(field, code, message, null);
    }

    /** TODO §102 (U8) — la valeur est déjà tenue par un dossier non clôturé du même type. */
    public static FieldValidationError notUnique(String field, String existingReference) {
        Objects.requireNonNull(existingReference, "existingReference");
        return new FieldValidationError(
                field, "UNIQUE", "Value already used by dossier " + existingReference, existingReference);
    }

    public static FieldValidationError required(String field) {
        return new FieldValidationError(field, "REQUIRED", "Field is required");
    }

    public static FieldValidationError typeMismatch(String field, FieldType expected) {
        return new FieldValidationError(
                field, "TYPE_MISMATCH", "Expected type " + expected.name());
    }

    public static FieldValidationError invalidOption(String field) {
        return new FieldValidationError(field, "INVALID_OPTION", "Value not in allowed options");
    }

    /** TODO §76 — valeur structurée mal formée (montant, référence, ligne de groupe) ; le message ne recopie jamais la valeur. */
    public static FieldValidationError invalidValue(String field, String message) {
        return new FieldValidationError(field, "INVALID_VALUE", message);
    }

    /** TODO §80 : un champ calculé n'est jamais saisi. */
    public static FieldValidationError computedReadOnly(String field) {
        return new FieldValidationError(field, "COMPUTED_READ_ONLY", "Computed field: its value cannot be set");
    }

    /** TODO §80 : valeur fournie pour un champ que la règle de visibilité rend sans objet. */
    public static FieldValidationError notApplicable(String field) {
        return new FieldValidationError(field, "NOT_APPLICABLE", "Field is not applicable in the current dossier state");
    }

    /** TODO §80 : règle (calcul, visibilité, obligation) en erreur — le message ne recopie jamais de valeur. */
    public static FieldValidationError ruleFailed(String field, String message) {
        return new FieldValidationError(field, "RULE_FAILED", message);
    }

    public static FieldValidationError unknownField(String field) {
        return new FieldValidationError(field, "UNKNOWN_FIELD", "Unknown field");
    }
}
