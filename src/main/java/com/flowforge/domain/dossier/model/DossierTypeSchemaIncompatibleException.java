package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

/** D-SCHEMA-COMPAT=B — raised by callers (not the compatibility check itself) that choose to block. */
public class DossierTypeSchemaIncompatibleException extends IllegalStateException {

    public static final String CODE = "SCHEMA_INCOMPATIBLE";

    private final int incompatibleCount;
    private final List<String> sampleReferences;
    private final List<UniqueDuplicate> duplicates;
    /** Nombre total de valeurs en double, y compris celles absentes de {@link #duplicates} quand la liste est bornée. */
    private final int duplicateCount;

    public DossierTypeSchemaIncompatibleException(int incompatibleCount, List<String> sampleReferences) {
        this(incompatibleCount, sampleReferences, List.of());
    }

    /** TODO §102 (U10) — {@code duplicates} : valeurs d'un champ unique tenues par plusieurs dossiers ouverts. */
    public DossierTypeSchemaIncompatibleException(
            int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates) {
        this(incompatibleCount, sampleReferences, duplicates, duplicates.size());
    }

    /**
     * TODO §102 — {@code duplicateCount} est le total des valeurs en double. {@code duplicates} peut n'en citer que 20.
     */
    public DossierTypeSchemaIncompatibleException(
            int incompatibleCount, List<String> sampleReferences, List<UniqueDuplicate> duplicates, int duplicateCount) {
        super("Schema change incompatible with " + incompatibleCount + " existing dossier(s)");
        this.incompatibleCount = incompatibleCount;
        this.sampleReferences = List.copyOf(Objects.requireNonNull(sampleReferences, "sampleReferences"));
        this.duplicates = List.copyOf(Objects.requireNonNull(duplicates, "duplicates"));
        if (duplicateCount < this.duplicates.size()) {
            throw new IllegalArgumentException("duplicateCount");
        }
        this.duplicateCount = duplicateCount;
    }

    public List<UniqueDuplicate> duplicates() {
        return duplicates;
    }

    public int duplicateCount() {
        return duplicateCount;
    }

    public int incompatibleCount() {
        return incompatibleCount;
    }

    public List<String> sampleReferences() {
        return sampleReferences;
    }
}
