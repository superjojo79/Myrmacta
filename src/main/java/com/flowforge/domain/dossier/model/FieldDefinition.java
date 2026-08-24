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
        String group,
        Boolean sensitive
) {
    public FieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
        // Jackson may pass null when the JSON key is absent (FAIL_ON_NULL_FOR_PRIMITIVES).
        sensitive = Boolean.TRUE.equals(sensitive);
    }

    /** Convenience — defaults {@code sensitive} to {@code false}. */
    public FieldDefinition(
            String name,
            String label,
            FieldType type,
            boolean required,
            String defaultValue,
            List<String> options,
            String group) {
        this(name, label, type, required, defaultValue, options, group, false);
    }

    /** Canonical boolean reader for the {@code sensitive} metadata (redaction/exports later). */
    public boolean isSensitive() {
        return Boolean.TRUE.equals(sensitive);
    }

    /**
     * D-LOG-GUARD: never prints {@code defaultValue} — it can itself carry a business-prefilled
     * value on a field marked {@link #isSensitive()}. Everything else here is metadata (names,
     * type, flags), safe to print.
     */
    @Override
    public String toString() {
        return "FieldDefinition[name=" + name
                + ", label=" + label
                + ", type=" + type
                + ", required=" + required
                + ", defaultValue=" + (defaultValue == null ? "null" : "<redacted>")
                + ", options=" + options
                + ", group=" + group
                + ", sensitive=" + sensitive
                + "]";
    }
}
