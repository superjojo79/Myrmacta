package be.myrmacta.domain.dossier.model;

import java.util.List;
import java.util.Objects;

/**
 * TODO §102 (issue #184, U10) — valeur d'un champ unique tenue par plusieurs dossiers ouverts, avec leurs références
 * (triées). Montrée à l'administrateur qui active {@code unique} : la valeur n'est jamais sensible.
 */
public record UniqueDuplicate(String field, String value, List<String> references, int referenceCount) {
    /** Les références tenues sont toutes listées : {@code referenceCount} vaut leur nombre. */
    public UniqueDuplicate(String field, String value, List<String> references) {
        this(field, value, references, references.size());
    }

    public UniqueDuplicate {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
        references = List.copyOf(references);
        if (referenceCount < references.size()) {
            throw new IllegalArgumentException("referenceCount");
        }
    }
}
