package com.flowforge.domain.dossier.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DossierFieldValidator {

    private static final Pattern MONEY_AMOUNT = Pattern.compile("-?\\d{1,15}(\\.\\d{1,6})?");
    private static final Pattern SYSTEM_ID = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final BigDecimal MONEY_LIMIT = new BigDecimal("1000000000000000");
    private static final Set<String> MONEY_KEYS = Set.of("amount", "currency");
    private static final Set<String> REF_KEYS = Set.of("system", "id", "label");
    private static final int REF_TEXT_MAX = 200;
    /** Revue adversariale D1 (M4) : un groupe de 500 lignes × 20 cellules ne doit pas porter des centaines de Mo. */
    private static final int CELL_TEXT_MAX = 10_000;
    private static final int GROUP_TEXT_MAX = 1_000_000;
    private static final int FIELDS_TEXT_MAX = 1_048_576;
    /** Un montant JSON (nombre) n'est sûr que si un double le représente exactement : 15 chiffres significatifs au plus. */
    private static final int DOUBLE_SAFE_DIGITS = 15;
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9_.-]{1,50}");

    public Map<String, Object> applyDefaults(List<FieldDefinition> definitions, Map<String, Object> values) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, Object> merged = new HashMap<>(values == null ? Map.of() : values);
        for (FieldDefinition def : definitions) {
            if (def.defaultValue() != null && !merged.containsKey(def.name())) {
                merged.put(def.name(), coerceDefault(def));
            }
        }
        return Map.copyOf(merged);
    }

    public List<FieldValidationError> validate(
            List<FieldDefinition> definitions, Map<String, Object> values, boolean allowUnknownFields) {
        Objects.requireNonNull(definitions, "definitions");
        Map<String, Object> vals = values == null ? Map.of() : values;
        if (textLength(vals, FIELDS_TEXT_MAX) > FIELDS_TEXT_MAX) {
            return List.of(FieldValidationError.invalidValue(
                    "fields", "Fields exceed " + FIELDS_TEXT_MAX + " characters of text in total"));
        }
        List<FieldValidationError> errors = new ArrayList<>(validateValues(definitions, vals, ""));

        if (!allowUnknownFields) {
            Set<String> known = definitions.stream().map(FieldDefinition::name).collect(Collectors.toSet());
            for (String name : vals.keySet()) {
                if (!known.contains(name)) {
                    errors.add(FieldValidationError.unknownField(name));
                }
            }
        }
        return List.copyOf(errors);
    }

    /**
     * Forme canonique des valeurs structurées (TODO §76) — à appliquer <b>après</b> une validation réussie : un montant
     * devient une chaîne décimale à l'échelle de sa devise ({@code 12.5 EUR} → {@code "12.50"}), une référence est
     * nettoyée, les lignes d'un groupe sont normalisées une à une. Les autres valeurs sont renvoyées telles quelles ; une
     * valeur qui ne se normalise pas est laissée intacte (la validation est le seul juge).
     */
    public Map<String, Object> normalize(List<FieldDefinition> definitions, Map<String, Object> values) {
        Objects.requireNonNull(definitions, "definitions");
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(values);
        // Une valeur nulle (cellule d'une ligne, par exemple) est une absence : `Map.copyOf` la refuserait (NPE, HTTP 500).
        out.values().removeIf(Objects::isNull);
        for (FieldDefinition def : definitions) {
            Object value = out.get(def.name());
            if (value != null) {
                out.put(def.name(), normalizeValue(def, value));
            }
        }
        return Map.copyOf(out);
    }

    // ---- validation -----------------------------------------------------------------------------------------------

    private List<FieldValidationError> validateValues(
            List<FieldDefinition> definitions, Map<String, Object> vals, String prefix) {
        List<FieldValidationError> errors = new ArrayList<>();
        for (FieldDefinition def : definitions) {
            String path = prefix + def.name();
            Object value = vals.get(def.name());
            if (def.type() == FieldType.REPEATABLE_GROUP) {
                validateGroup(def, path, value, errors);
                continue;
            }
            if (def.required() && isBlank(value)) {
                errors.add(FieldValidationError.required(path));
                continue;
            }
            if (value == null || isBlank(value) && def.type() != FieldType.STRING) {
                continue;
            }
            if (!matchesType(value, def.type())) {
                errors.add(FieldValidationError.typeMismatch(path, def.type()));
                continue;
            }
            if (!prefix.isEmpty() && value instanceof String text && text.length() > CELL_TEXT_MAX) {
                errors.add(FieldValidationError.invalidValue(path, "Text exceeds " + CELL_TEXT_MAX + " characters"));
                continue;
            }
            // TODO §102 (U13) : la forme canonique d'une valeur unique doit tenir dans la clé de dossier_unique_value.
            if (def.isUnique() && lengthOf(UniqueFieldKeys.canonical(def.type(), value)) > UniqueFieldKeys.MAX_VALUE_CHARS) {
                errors.add(FieldValidationError.invalidValue(
                        path, "A unique value has at most " + UniqueFieldKeys.MAX_VALUE_CHARS + " characters"));
                continue;
            }
            if (def.type() == FieldType.SELECT
                    && value != null
                    && !def.options().contains(String.valueOf(value))) {
                errors.add(FieldValidationError.invalidOption(path));
            }
            if (def.type() == FieldType.MONEY) {
                validateMoney(def, path, (Map<?, ?>) value).ifPresent(errors::add);
            } else if (def.type() == FieldType.EXTERNAL_REF) {
                validateReference(def, path, (Map<?, ?>) value).ifPresent(errors::add);
            }
        }
        return errors;
    }

    private void validateGroup(FieldDefinition def, String path, Object value, List<FieldValidationError> errors) {
        if (value == null) {
            if (def.required()) {
                errors.add(FieldValidationError.required(path));
            } else if (def.minRows() > 0) {
                // minRows vaut pour un groupe absent comme pour un groupe vide.
                errors.add(FieldValidationError.invalidValue(
                        path, "Expected between " + def.minRows() + " and " + def.maxRows() + " rows"));
            }
            return;
        }
        if (!(value instanceof List<?> rows)) {
            errors.add(FieldValidationError.typeMismatch(path, FieldType.REPEATABLE_GROUP));
            return;
        }
        if (def.required() && rows.isEmpty()) {
            errors.add(FieldValidationError.required(path));
            return;
        }
        if (rows.size() < def.minRows() || rows.size() > def.maxRows()) {
            errors.add(FieldValidationError.invalidValue(
                    path, "Expected between " + def.minRows() + " and " + def.maxRows() + " rows"));
            return;
        }
        if (textLength(rows, GROUP_TEXT_MAX) > GROUP_TEXT_MAX) {
            errors.add(FieldValidationError.invalidValue(
                    path, "Rows exceed " + GROUP_TEXT_MAX + " characters of text in total"));
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            String rowPath = path + "[" + i + "]";
            if (!(rows.get(i) instanceof Map<?, ?> row)) {
                errors.add(FieldValidationError.typeMismatch(rowPath, FieldType.REPEATABLE_GROUP));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> typedRow = (Map<String, Object>) row;
            errors.addAll(validateValues(def.fields(), typedRow, rowPath + "."));
            Set<String> known = def.fields().stream().map(FieldDefinition::name).collect(Collectors.toSet());
            for (Object key : row.keySet()) {
                if (!known.contains(String.valueOf(key))) {
                    // La clé vient de l'appelant : recopiée dans le chemin d'erreur seulement si c'est un identifiant simple.
                    String shown = SAFE_KEY.matcher(String.valueOf(key)).matches() ? String.valueOf(key) : "?";
                    errors.add(FieldValidationError.unknownField(rowPath + "." + shown));
                }
            }
        }
    }

    private static Optional<FieldValidationError> validateMoney(FieldDefinition def, String path, Map<?, ?> value) {
        if (!value.keySet().equals(MONEY_KEYS)) {
            return Optional.of(FieldValidationError.invalidValue(path, "A money value needs exactly amount and currency"));
        }
        if (!(value.get("currency") instanceof String code) || !FieldDefinition.isIsoCurrency(code)) {
            return Optional.of(FieldValidationError.invalidValue(path, "currency must be an ISO 4217 code in upper case"));
        }
        if (!def.options().isEmpty() && !def.options().contains(code)) {
            return Optional.of(FieldValidationError.invalidOption(path));
        }
        Optional<BigDecimal> amount = parseAmount(value.get("amount"));
        if (amount.isEmpty() || amount.get().abs().compareTo(MONEY_LIMIT) >= 0) {
            return Optional.of(FieldValidationError.invalidValue(path, "amount must be a decimal number below 10^15"));
        }
        int digits = Currency.getInstance(code).getDefaultFractionDigits();
        if (amount.get().stripTrailingZeros().scale() > digits) {
            return Optional.of(FieldValidationError.invalidValue(
                    path, "amount has more than " + digits + " decimals for " + code));
        }
        return Optional.empty();
    }

    private static Optional<FieldValidationError> validateReference(FieldDefinition def, String path, Map<?, ?> value) {
        if (!REF_KEYS.containsAll(value.keySet()) || !(value.get("system") instanceof String system)
                || !(value.get("id") instanceof String id)) {
            return Optional.of(FieldValidationError.invalidValue(path, "A reference needs a system and an id (and may carry a label)"));
        }
        if (!SYSTEM_ID.matcher(system).matches()) {
            return Optional.of(FieldValidationError.invalidValue(path, "system is not a valid identifier"));
        }
        if (!def.options().isEmpty() && !def.options().contains(system)) {
            return Optional.of(FieldValidationError.invalidOption(path));
        }
        if (!plainText(id, true) || (value.containsKey("label") && !(value.get("label") instanceof String label
                && plainText(label, false)))) {
            return Optional.of(FieldValidationError.invalidValue(path, "id and label must be plain text of at most 200 characters"));
        }
        return Optional.empty();
    }

    private static boolean plainText(String text, boolean required) {
        String trimmed = text.strip();
        return (!required || !trimmed.isEmpty())
                && trimmed.length() <= REF_TEXT_MAX
                && text.chars().noneMatch(Character::isISOControl);
    }

    private static Optional<BigDecimal> parseAmount(Object raw) {
        try {
            return switch (raw) {
                case String s when MONEY_AMOUNT.matcher(s).matches() -> Optional.of(new BigDecimal(s));
                case Integer i -> Optional.of(BigDecimal.valueOf(i));
                case Long l -> Optional.of(BigDecimal.valueOf(l));
                case Short s -> Optional.of(BigDecimal.valueOf(s));
                case Byte b -> Optional.of(BigDecimal.valueOf(b));
                case BigInteger b -> Optional.of(new BigDecimal(b));
                case BigDecimal b -> Optional.of(b);
                // Jackson lit `99999999999999.99` comme le Double 9.999999999999998E13 : accepté, il deviendrait « …98 » en
                // silence. Un double n'est accepté que s'il représente exactement ce qu'il affiche (≤ 15 chiffres
                // significatifs) ; au-delà, le montant doit être envoyé en texte. Float n'est jamais exact : refusé.
                case Double d when Double.isFinite(d) && BigDecimal.valueOf(d).precision() <= DOUBLE_SAFE_DIGITS ->
                        Optional.of(BigDecimal.valueOf(d));
                case null, default -> Optional.empty();
            };
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    // ---- normalisation --------------------------------------------------------------------------------------------

    private Object normalizeValue(FieldDefinition def, Object value) {
        switch (def.type()) {
            case MONEY -> {
                if (value instanceof Map<?, ?> m && validateMoney(def, def.name(), m).isEmpty()) {
                    String code = (String) m.get("currency");
                    BigDecimal amount = parseAmount(m.get("amount")).orElseThrow();
                    int digits = Currency.getInstance(code).getDefaultFractionDigits();
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("amount", amount.setScale(digits, java.math.RoundingMode.UNNECESSARY).toPlainString());
                    out.put("currency", code);
                    return out;
                }
            }
            case EXTERNAL_REF -> {
                if (value instanceof Map<?, ?> m && validateReference(def, def.name(), m).isEmpty()) {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("system", m.get("system"));
                    out.put("id", ((String) m.get("id")).strip());
                    if (m.get("label") instanceof String label && !label.isBlank()) {
                        out.put("label", label.strip());
                    }
                    return out;
                }
            }
            case REPEATABLE_GROUP -> {
                if (value instanceof List<?> rows) {
                    List<Object> out = new ArrayList<>();
                    for (Object row : rows) {
                        if (row instanceof Map<?, ?> r) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> typed = (Map<String, Object>) r;
                            out.add(normalize(def.fields(), typed));
                        } else {
                            out.add(row);
                        }
                    }
                    return List.copyOf(out);
                }
            }
            default -> { }
        }
        return value;
    }

    // ---- divers ---------------------------------------------------------------------------------------------------

    /** Longueur cumulée des textes d'une valeur JSON ; s'arrête dès que {@code cap} est dépassé (pas de parcours inutile). */
    private static long textLength(Object value, long cap) {
        long total = 0;
        if (value instanceof String s) {
            return s.length();
        }
        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                total += textLength(item, cap - total);
                if (total > cap) {
                    return total;
                }
            }
        } else if (value instanceof Iterable<?> items) {
            for (Object item : items) {
                total += textLength(item, cap - total);
                if (total > cap) {
                    return total;
                }
            }
        }
        return total;
    }

    private static Object coerceDefault(FieldDefinition def) {
        String raw = def.defaultValue();
        return switch (def.type()) {
            case NUMBER -> {
                try {
                    if (raw.contains(".")) {
                        yield Double.parseDouble(raw);
                    }
                    yield Long.parseLong(raw);
                } catch (NumberFormatException ex) {
                    yield raw;
                }
            }
            case BOOLEAN -> Boolean.parseBoolean(raw);
            default -> raw;
        };
    }

    private static int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }

    private static boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        return false;
    }

    private static boolean matchesType(Object value, FieldType type) {
        return switch (type) {
            case STRING, FILE -> value instanceof String;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case DATE -> value instanceof LocalDate || (value instanceof String s && parseDate(s));
            case SELECT -> value instanceof String;
            case MONEY, EXTERNAL_REF -> value instanceof Map<?, ?>;
            case REPEATABLE_GROUP -> value instanceof List<?>;
        };
    }

    private static boolean parseDate(String s) {
        try {
            LocalDate.parse(s);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
