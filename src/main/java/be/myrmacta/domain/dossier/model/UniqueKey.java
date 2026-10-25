package be.myrmacta.domain.dossier.model;

import java.util.Objects;

/**
 * TODO §102 (issue #184) — valeur d'un champ unique sous sa forme canonique ({@link UniqueFieldKeys}). Jamais journalisée :
 * {@link #toString()} masque la valeur, qui reste une donnée métier.
 */
public record UniqueKey(String field, String value) {
    public UniqueKey {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(value, "value");
    }

    @Override
    public String toString() {
        return "UniqueKey[field=" + field + ", value=<redacted>]";
    }
}
