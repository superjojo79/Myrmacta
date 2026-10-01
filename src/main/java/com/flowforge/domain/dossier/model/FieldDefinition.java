package com.flowforge.domain.dossier.model;

import java.util.Currency;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Définition d'un champ dynamique. TODO §76 (D1) : trois types structurés.
 *
 * <ul>
 *   <li>{@link FieldType#MONEY} — {@code options} = devises autorisées (ISO 4217, facultatif) ;</li>
 *   <li>{@link FieldType#EXTERNAL_REF} — {@code options} = systèmes autorisés (facultatif) ;</li>
 *   <li>{@link FieldType#REPEATABLE_GROUP} — {@code fields} = sous-champs (plats, non sensibles, jamais {@code FILE} ni
 *       groupe), {@code minRows}/{@code maxRows} = bornes du nombre de lignes (défaut 0 et 100, plafond 500). La
 *       sensibilité se pose sur le <em>groupe entier</em> : un sous-champ sensible demanderait un masquage récursif dans
 *       chaque export, modèle et recherche, qu'on ne garantit pas.</li>
 * </ul>
 *
 * <p>TODO §80 (D1, règles — LC2) : trois expressions JSONata bornées facultatives, sur les champs <b>simples, non sensibles</b>
 * du dossier ({@code dossier.fields.x}) :
 * <ul>
 *   <li>{@code formula} — champ <b>calculé</b> : valeur dérivée, recalculée à chaque enregistrement, en lecture seule ;</li>
 *   <li>{@code visibleWhen} — le champ n'est applicable que si l'expression est vraie ;</li>
 *   <li>{@code requiredWhen} — le champ est obligatoire si l'expression est vraie (en plus de {@code required}).</li>
 * </ul>
 */
public record FieldDefinition(
        String name,
        String label,
        FieldType type,
        boolean required,
        String defaultValue,
        List<String> options,
        String group,
        Boolean sensitive,
        List<FieldDefinition> fields,
        Integer minRows,
        Integer maxRows,
        String formula,
        String visibleWhen,
        String requiredWhen
) {
    public static final int MAX_EXPRESSION_CHARS = 2000;
    public static final int MAX_GROUP_FIELDS = 20;
    public static final int DEFAULT_MAX_ROWS = 100;
    public static final int HARD_MAX_ROWS = 500;

    private static final Pattern CURRENCY_CODE = Pattern.compile("[A-Z]{3}");
    private static final Pattern SYSTEM_ID = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final Set<FieldType> GROUP_SUBFIELD_TYPES = EnumSet.of(
            FieldType.STRING, FieldType.NUMBER, FieldType.DATE, FieldType.BOOLEAN, FieldType.SELECT, FieldType.MONEY,
            FieldType.EXTERNAL_REF);
    private static final Set<FieldType> NO_DEFAULT_TYPES =
            EnumSet.of(FieldType.MONEY, FieldType.EXTERNAL_REF, FieldType.REPEATABLE_GROUP);

    public FieldDefinition {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
        options = options == null ? List.of() : List.copyOf(options);
        // Jackson may pass null when the JSON key is absent (FAIL_ON_NULL_FOR_PRIMITIVES).
        sensitive = Boolean.TRUE.equals(sensitive);
        fields = fields == null ? List.of() : List.copyOf(fields);

        formula = blankToNull(formula);
        visibleWhen = blankToNull(visibleWhen);
        requiredWhen = blankToNull(requiredWhen);
        for (String expression : new String[] {formula, visibleWhen, requiredWhen}) {
            if (expression != null && expression.length() > MAX_EXPRESSION_CHARS) {
                throw new IllegalArgumentException(
                        "an expression exceeds " + MAX_EXPRESSION_CHARS + " characters (field: " + name + ")");
            }
        }
        if (formula != null) {
            if (Boolean.TRUE.equals(sensitive)) {
                throw new IllegalArgumentException("a computed field cannot be sensitive (field: " + name + ")");
            }
            if (type == FieldType.REPEATABLE_GROUP || type == FieldType.FILE) {
                throw new IllegalArgumentException("a " + type + " field cannot be computed (field: " + name + ")");
            }
            if (required || defaultValue != null || visibleWhen != null || requiredWhen != null) {
                throw new IllegalArgumentException("a computed field is always present and read-only: it takes no 'required', "
                        + "default value, visibleWhen or requiredWhen (field: " + name + ")");
            }
        }
        if (type != FieldType.REPEATABLE_GROUP && (!fields.isEmpty() || minRows != null || maxRows != null)) {
            throw new IllegalArgumentException(
                    "only a REPEATABLE_GROUP can declare sub-fields, minRows or maxRows (field: " + name + ")");
        }
        if (defaultValue != null && NO_DEFAULT_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "a default value is not supported for type " + type + " (field: " + name + ")");
        }
        switch (type) {
            case MONEY -> options.forEach(code -> requireIsoCurrency(name, code));
            case EXTERNAL_REF -> options.forEach(system -> {
                if (!SYSTEM_ID.matcher(system).matches()) {
                    throw new IllegalArgumentException(
                            "EXTERNAL_REF option is not a valid system identifier (field: " + name + ")");
                }
            });
            case REPEATABLE_GROUP -> {
                if (fields.isEmpty()) {
                    throw new IllegalArgumentException(
                            "a REPEATABLE_GROUP needs at least one sub-field (field: " + name + ")");
                }
                if (!options.isEmpty()) {
                    throw new IllegalArgumentException("a REPEATABLE_GROUP does not take options (field: " + name + ")");
                }
                if (fields.size() > MAX_GROUP_FIELDS) {
                    throw new IllegalArgumentException(
                            "a group has at most " + MAX_GROUP_FIELDS + " sub-fields (field: " + name + ")");
                }
                Set<String> seen = new HashSet<>();
                for (FieldDefinition sub : fields) {
                    if (sub.name().isBlank()) {
                        throw new IllegalArgumentException("a sub-field of a group needs a name (group: " + name + ")");
                    }
                    if (sub.defaultValue() != null && !sub.defaultValue().isBlank()) {
                        throw new IllegalArgumentException("a sub-field of a group cannot have a default value (field: "
                                + sub.name() + "): it would never be applied");
                    }
                    if (!seen.add(sub.name())) {
                        throw new IllegalArgumentException(
                                "duplicate sub-field name: " + sub.name() + " (group: " + name + ")");
                    }
                    if (!GROUP_SUBFIELD_TYPES.contains(sub.type())) {
                        throw new IllegalArgumentException("sub-field type " + sub.type()
                                + " is not allowed in a group (field: " + sub.name() + ")");
                    }
                    if (sub.formula() != null || sub.visibleWhen() != null || sub.requiredWhen() != null) {
                        throw new IllegalArgumentException("a sub-field of a group cannot carry rules (field: " + sub.name() + ")");
                    }
                    if (sub.isSensitive()) {
                        throw new IllegalArgumentException("a sub-field of a group cannot be sensitive: mark the group "
                                + name + " itself");
                    }
                }
                minRows = minRows == null ? 0 : minRows;
                maxRows = maxRows == null ? DEFAULT_MAX_ROWS : maxRows;
                if (minRows < 0) {
                    throw new IllegalArgumentException("minRows must not be negative (group: " + name + ")");
                }
                if (maxRows < 1) {
                    throw new IllegalArgumentException("maxRows must be at least 1 (group: " + name + ")");
                }
                if (maxRows > HARD_MAX_ROWS) {
                    throw new IllegalArgumentException(
                            "maxRows must be at most " + HARD_MAX_ROWS + " (group: " + name + ")");
                }
                if (minRows > maxRows) {
                    throw new IllegalArgumentException("minRows must not exceed maxRows (group: " + name + ")");
                }
            }
            default -> { }
        }
    }

    /** Sans règles (ni calcul, ni visibilité, ni obligation conditionnelle) : tout ce qui existait avant TODO §80. */
    public FieldDefinition(
            String name,
            String label,
            FieldType type,
            boolean required,
            String defaultValue,
            List<String> options,
            String group,
            Boolean sensitive,
            List<FieldDefinition> fields,
            Integer minRows,
            Integer maxRows) {
        this(name, label, type, required, defaultValue, options, group, sensitive, fields, minRows, maxRows, null, null, null);
    }

    /**
     * {@code true} si le champ est calculé (valeur dérivée, jamais saisie). Nommé {@code hasFormula} et non {@code isComputed} :
     * Jackson y verrait une propriété « computed » absente du constructeur, que la relecture JSON des définitions refuserait.
     */
    public boolean hasFormula() {
        return formula != null;
    }

    /** Copie avec une obligation différente (règles de visibilité / d'obligation appliquées au moment de la validation). */
    public FieldDefinition withRequired(boolean newRequired) {
        return new FieldDefinition(name, label, type, newRequired, defaultValue, options, group, sensitive, fields, minRows,
                maxRows, formula, visibleWhen, requiredWhen);
    }

    /**
     * Copie « non applicable » : ni obligatoire ni borne minimale de lignes (un groupe masqué avec {@code minRows > 0}
     * resterait sinon impossible à enregistrer).
     */
    public FieldDefinition withHidden() {
        return new FieldDefinition(name, label, type, false, defaultValue, options, group, sensitive, fields,
                minRows == null ? null : 0, maxRows, formula, visibleWhen, requiredWhen);
    }

    /** Champ scalaire historique : sans sous-champs ni bornes de lignes. */
    public FieldDefinition(
            String name,
            String label,
            FieldType type,
            boolean required,
            String defaultValue,
            List<String> options,
            String group,
            Boolean sensitive) {
        this(name, label, type, required, defaultValue, options, group, sensitive, List.of(), null, null);
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

    /**
     * Code de devise ISO 4217 connu du JDK (les pseudo-devises sans décimales connues, comme XXX, sont refusées). Le JDK
     * connaît aussi des devises retirées (FRF, DEM…) : elles sont acceptées — restreindre les devises d'un champ avec
     * {@code options} pour n'autoriser que celles qu'on veut.
     */
    public static boolean isIsoCurrency(String code) {
        if (code == null || !CURRENCY_CODE.matcher(code).matches()) {
            return false;
        }
        try {
            return Currency.getInstance(code).getDefaultFractionDigits() >= 0;
        } catch (IllegalArgumentException unknown) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void requireIsoCurrency(String field, String code) {
        if (!isIsoCurrency(code)) {
            throw new IllegalArgumentException(
                    "MONEY option must be an ISO 4217 currency code in upper case (field: " + field + ")");
        }
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
                + ", fields=" + fields.size()
                + ", minRows=" + minRows
                + ", maxRows=" + maxRows
                + ", computed=" + hasFormula()
                + ", visibleWhen=" + (visibleWhen != null)
                + ", requiredWhen=" + (requiredWhen != null)
                + "]";
    }
}
