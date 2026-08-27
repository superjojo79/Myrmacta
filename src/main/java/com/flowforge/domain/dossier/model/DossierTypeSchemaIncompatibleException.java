package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

/** D-SCHEMA-COMPAT=B — raised by callers (not the compatibility check itself) that choose to block. */
public class DossierTypeSchemaIncompatibleException extends IllegalStateException {

    public static final String CODE = "SCHEMA_INCOMPATIBLE";

    private final int incompatibleCount;
    private final List<String> sampleReferences;

    public DossierTypeSchemaIncompatibleException(int incompatibleCount, List<String> sampleReferences) {
        super("Schema change incompatible with " + incompatibleCount + " existing dossier(s)");
        this.incompatibleCount = incompatibleCount;
        this.sampleReferences = List.copyOf(Objects.requireNonNull(sampleReferences, "sampleReferences"));
    }

    public int incompatibleCount() {
        return incompatibleCount;
    }

    public List<String> sampleReferences() {
        return sampleReferences;
    }
}
