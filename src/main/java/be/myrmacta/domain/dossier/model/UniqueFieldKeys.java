package be.myrmacta.domain.dossier.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §102 (issue #184) — clés d'unicité tenues par un dossier : une par champ {@code unique} dont la valeur n'est pas vide
 * (U7), aucune pour un dossier clôturé ou archivé (D-UNIQUE-SCOPE), valeur sous forme canonique (D-UNIQUE-COMPARE).
 * Pur : les champs calculés sont recalculés en amont ({@link be.myrmacta.domain.dossier.DossierUniqueKeys}).
 */
public final class UniqueFieldKeys {

    /** U13 — longueur maximale d'une valeur canonique, bornée par la clé de {@code dossier_unique_value}. */
    public static final int MAX_VALUE_CHARS = 500;

    private UniqueFieldKeys() {}

    public static Set<UniqueKey> of(List<FieldDefinition> definitions, DossierStatus status, Map<String, Object> fields) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(status, "status");
        if (status.blocksMutation() || fields == null || fields.isEmpty()) {
            return Set.of();
        }
        Set<UniqueKey> keys = new LinkedHashSet<>();
        for (FieldDefinition def : definitions) {
            if (!def.isUnique()) {
                continue;
            }
            String value = canonical(def.type(), fields.get(def.name()));
            if (value != null) {
                keys.add(new UniqueKey(def.name(), value));
            }
        }
        return Set.copyOf(keys);
    }

    public static boolean hasUniqueField(List<FieldDefinition> definitions) {
        return definitions.stream().anyMatch(FieldDefinition::isUnique);
    }

    /**
     * Forme canonique (U6) : texte tel quel ; nombre sans zéro non significatif ni exposant ({@code 1.50} → {@code 1.5},
     * {@code 1E+2} → {@code 100}) ; date ISO. {@code null} pour une valeur absente ou vide (U7).
     */
    public static String canonical(FieldType type, Object value) {
        if (value == null || value instanceof String s && s.isBlank()) {
            return null;
        }
        return switch (type) {
            case NUMBER -> canonicalNumber(value);
            case DATE -> value instanceof LocalDate date ? date.toString() : String.valueOf(value);
            default -> String.valueOf(value);
        };
    }

    private static String canonicalNumber(Object value) {
        BigDecimal number;
        try {
            number = switch (value) {
                case BigDecimal b -> b;
                case Double d -> BigDecimal.valueOf(d);
                case Float f -> new BigDecimal(f.toString());
                case Number n -> new BigDecimal(n.toString());
                default -> new BigDecimal(String.valueOf(value).strip());
            };
        } catch (NumberFormatException notANumber) {
            return String.valueOf(value); // la validation de type refuse déjà ces valeurs : jamais atteint en écriture
        }
        if (number.signum() == 0) {
            return "0";
        }
        return number.stripTrailingZeros().toPlainString();
    }
}
