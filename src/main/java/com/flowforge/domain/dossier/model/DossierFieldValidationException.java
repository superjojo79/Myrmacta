package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

public class DossierFieldValidationException extends RuntimeException {
    private final List<FieldValidationError> errors;

    public DossierFieldValidationException(List<FieldValidationError> errors) {
        super(summary(errors));
        this.errors = List.copyOf(Objects.requireNonNull(errors, "errors"));
    }

    /**
     * TODO §102 — le message nomme chaque champ en erreur, son code et son explication (jamais la valeur saisie, que les
     * messages ne recopient pas) : c'est tout ce que reçoit un appelant MCP, qui ne voit pas {@link #errors()}.
     */
    private static String summary(List<FieldValidationError> errors) {
        Objects.requireNonNull(errors, "errors");
        if (errors.isEmpty()) {
            return "Dossier field validation failed";
        }
        return "Dossier field validation failed: " + errors.stream()
                .map(error -> error.field() + " " + error.code() + " (" + error.message() + ")")
                .collect(java.util.stream.Collectors.joining("; "));
    }

    public List<FieldValidationError> errors() {
        return errors;
    }
}
