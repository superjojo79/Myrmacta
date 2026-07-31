package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

public record FieldDefinition(
        String name,
        String label,
        FieldType type,
        boolean required,
        String defaultValue,
        List<String> options,
        String group
) {
    public FieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
