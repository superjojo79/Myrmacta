package be.myrmacta.domain.workflow.palette;

import java.util.List;
import java.util.Objects;

/**
 * Paramètre typé d'une brique. {@code name} est le nom de l'attribut {@code flowable:<name>} (binding
 * {@link ParameterBinding#ATTRIBUTE}) — identique au nom de la variable de process de même sens — ou l'identifiant
 * logique du paramètre pour les autres bindings.
 */
public record BlockParameter(
        String name,
        String label,
        ParameterType type,
        ParameterBinding binding,
        boolean required,
        String valueSource,
        List<String> enumValues,
        int maxLength,
        Integer min,
        Integer max,
        String help,
        boolean multiline) {

    /** Paramètres pouvant s'étendre sur plusieurs lignes (texte libre ou expression) — publié dans le catalogue pour l'éditeur. */
    public static final java.util.Set<String> MULTILINE_NAMES = java.util.Set.of("notificationBody", "formula", "fieldMapping", "searchCriteria",
            "precheckPieces", "precheckQuestions", "precheckRules", "connectorRequest");

    public BlockParameter(
            String name,
            String label,
            ParameterType type,
            ParameterBinding binding,
            boolean required,
            String valueSource,
            List<String> enumValues,
            int maxLength,
            Integer min,
            Integer max,
            String help) {
        this(name, label, type, binding, required, valueSource, enumValues, maxLength, min, max, help,
                MULTILINE_NAMES.contains(name));
    }

    public BlockParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(binding, "binding");
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        if (type == ParameterType.REFERENCE && (valueSource == null || valueSource.isBlank())) {
            throw new IllegalArgumentException("REFERENCE parameter '" + name + "' needs a valueSource");
        }
        if ((type == ParameterType.ENUM || type == ParameterType.ENUM_MULTI) && enumValues.isEmpty()) {
            throw new IllegalArgumentException("ENUM parameter '" + name + "' needs values");
        }
        if (type == ParameterType.INTEGER && (min == null || max == null)) {
            throw new IllegalArgumentException("INTEGER parameter '" + name + "' needs min and max");
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive for '" + name + "'");
        }
    }
}
