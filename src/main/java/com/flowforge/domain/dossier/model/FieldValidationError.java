package com.flowforge.domain.dossier.model;

import java.util.Objects;

public record FieldValidationError(String field, String code, String message) {
    public FieldValidationError {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
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

    public static FieldValidationError unknownField(String field) {
        return new FieldValidationError(field, "UNKNOWN_FIELD", "Unknown field");
    }
}
