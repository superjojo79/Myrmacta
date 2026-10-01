package com.flowforge.domain.dossier.model;

import java.util.List;
import java.util.Objects;

/**
 * TODO §81 (D2) — un critère de recherche sur un champ dynamique déclaré « cherchable », tel que saisi (valeurs en texte :
 * la conversion typée se fait dans le cas d'usage, d'après la définition du champ).
 */
public record FieldCondition(String field, Op op, List<String> values) {

    /** Plafond de critères par recherche (D-RECH-GUARD). */
    public static final int MAX_CONDITIONS = 5;
    /** Plafond de valeurs d'un critère {@code IN}. */
    public static final int MAX_IN_VALUES = 20;
    public static final int MAX_VALUE_CHARS = 200;

    public enum Op {
        /** Égalité. */
        EQ,
        /** Appartenance à une liste. */
        IN,
        /** Supérieur ou égal (nombre, date). */
        GTE,
        /** Inférieur ou égal (nombre, date). */
        LTE,
        /** Préfixe insensible à la casse (texte). */
        PREFIX
    }

    public FieldCondition {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(op, "op");
        values = values == null ? List.of() : List.copyOf(values);
        if (field.isBlank()) {
            throw new IllegalArgumentException("a field filter needs a field name");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("filter on '" + field + "' needs a value");
        }
        if (op != Op.IN && values.size() != 1) {
            throw new IllegalArgumentException("filter " + op + " on '" + field + "' takes exactly one value");
        }
        if (values.size() > MAX_IN_VALUES) {
            throw new IllegalArgumentException("filter on '" + field + "' takes at most " + MAX_IN_VALUES + " values");
        }
        for (String v : values) {
            if (v != null && v.indexOf('\u0000') >= 0) {
                // PostgreSQL ne sait ni stocker ni comparer NUL dans un texte ou un jsonb : 400 net plutôt qu'une erreur SQL.
                throw new IllegalArgumentException("filter value on '" + field + "' must not contain a NUL character");
            }
            if (v == null || v.isBlank() || v.length() > MAX_VALUE_CHARS) {
                throw new IllegalArgumentException(
                        "filter value on '" + field + "' must be non-blank and at most " + MAX_VALUE_CHARS + " characters");
            }
        }
    }
}
