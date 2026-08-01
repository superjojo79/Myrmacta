package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

public class DossierFieldValidationException extends RuntimeException {
    private final List<FieldValidationError> errors;

    public DossierFieldValidationException(List<FieldValidationError> errors) {
        super("Dossier field validation failed");
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    public List<FieldValidationError> errors() {
        return errors;
    }
}
