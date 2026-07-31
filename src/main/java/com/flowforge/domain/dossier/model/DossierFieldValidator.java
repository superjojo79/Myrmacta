package com.flowforge.domain.dossier.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DossierFieldValidator {

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
        List<FieldValidationError> errors = new ArrayList<>();

        for (FieldDefinition def : definitions) {
            Object value = vals.get(def.name());
            if (def.required() && isBlank(value)) {
                errors.add(FieldValidationError.required(def.name()));
                continue;
            }
            if (value == null || isBlank(value) && def.type() != FieldType.STRING) {
                continue;
            }
            if (!matchesType(value, def.type())) {
                errors.add(FieldValidationError.typeMismatch(def.name(), def.type()));
                continue;
            }
            if (def.type() == FieldType.SELECT
                    && value != null
                    && !def.options().contains(String.valueOf(value))) {
                errors.add(FieldValidationError.invalidOption(def.name()));
            }
        }

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
