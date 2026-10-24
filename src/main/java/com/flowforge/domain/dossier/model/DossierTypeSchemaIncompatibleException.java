package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

/** D-SCHEMA-COMPAT=B — raised by callers (not the compatibility check itself) that choose to block. */
public class DossierTypeSchemaIncompatibleException extends IllegalStateException {

    public static final String CODE = "SCHEMA_INCOMPATIBLE";

    private final int incompatibleCount;
    private final List<String> sampleReferences;
    private final List<UniqueDuplicate> duplicates;

    public DossierTypeSchemaIncompatibleException(int incompatibleCount, List<String> sampleReferences) {
        this(incompatibleCount, sampleReferences, List.of());
    }

    /** TODO §102 (U10) — {@code duplicates} : valeurs d'un champ unique tenues par plusieurs dossiers ouverts. */
    public DossierTypeSchemaIncompatibleException(
            int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates) {
        super("Schema change incompatible with " + incompatibleCount + " existing dossier(s)");
        this.incompatibleCount = incompatibleCount;
        this.sampleReferences = List.copyOf(Objects.requireNonNull(sampleReferences, "sampleReferences"));
        this.duplicates = List.copyOf(Objects.requireNonNull(duplicates, "duplicates"));
    }

    public List<UniqueDuplicate> duplicates() {
        return duplicates;
    }

    public int incompatibleCount() {
        return incompatibleCount;
    }

    public List<String> sampleReferences() {
        return sampleReferences;
    }
}
