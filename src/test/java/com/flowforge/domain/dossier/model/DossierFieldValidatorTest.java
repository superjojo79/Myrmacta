package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DossierFieldValidatorTest {

    private final DossierFieldValidator validator = new DossierFieldValidator();

    @Test
    void requiredMissing_returnsError() {
        var defs = List.of(field("name", FieldType.STRING, true, null));
        var errors = validator.validate(defs, Map.of(), false);
        assertThat(errors).extracting(FieldValidationError::field).containsExactly("name");
        assertThat(errors.getFirst().code()).isEqualTo("REQUIRED");
    }

    @Test
    void typeMismatch_number_returnsError() {
        var defs = List.of(field("amount", FieldType.NUMBER, false, null));
        var errors = validator.validate(defs, Map.of("amount", "not-a-number"), false);
        assertThat(errors).extracting(FieldValidationError::code).contains("TYPE_MISMATCH");
    }

    @Test
    void selectInvalidOption_returnsError() {
        var defs = List.of(new FieldDefinition(
                "status", "Status", FieldType.SELECT, true, null, List.of("A", "B"), null));
        var errors = validator.validate(defs, Map.of("status", "C"), false);
        assertThat(errors).extracting(FieldValidationError::code).contains("INVALID_OPTION");
    }

    @Test
    void sensitiveFlag_isIgnoredByValidation() {
        var defs = List.of(new FieldDefinition(
                "ssn", "SSN", FieldType.STRING, true, null, List.of(), null, true));
        assertThat(validator.validate(defs, Map.of("ssn", "123"), false)).isEmpty();
    }

    @Test
    void unknownField_rejectedWhenNotAllowed() {
        var defs = List.of(field("name", FieldType.STRING, false, null));
        var errors = validator.validate(defs, Map.of("extra", "x"), false);
        assertThat(errors).extracting(FieldValidationError::code).contains("UNKNOWN_FIELD");
    }

    @Test
    void unknownField_acceptedWhenAllowed() {
        var defs = List.of(field("name", FieldType.STRING, false, null));
        var errors = validator.validate(defs, Map.of("extra", "x"), true);
        assertThat(errors).isEmpty();
    }

    @Test
    void validPayload_returnsEmpty() {
        var defs = List.of(
                field("name", FieldType.STRING, true, null),
                field("amount", FieldType.NUMBER, false, null),
                field("ok", FieldType.BOOLEAN, false, null));
        var values = Map.<String, Object>of("name", "Alice", "amount", 42, "ok", true);
        assertThat(validator.validate(defs, values, false)).isEmpty();
    }

    @Test
    void applyDefaults_fillsMissingKeys() {
        var defs = List.of(field("lang", FieldType.STRING, false, "fr"));
        Map<String, Object> values = new HashMap<>();
        var merged = validator.applyDefaults(defs, values);
        assertThat(merged).containsEntry("lang", "fr");
        assertThat(values).doesNotContainKey("lang");
    }

    @Test
    void fileRequiredBlank_returnsError() {
        var defs = List.of(field("doc", FieldType.FILE, true, null));
        var errors = validator.validate(defs, Map.of("doc", "  "), false);
        assertThat(errors).extracting(FieldValidationError::code).contains("REQUIRED");
    }

    private static FieldDefinition field(
            String name, FieldType type, boolean required, String defaultValue) {
        return new FieldDefinition(name, name, type, required, defaultValue, List.of(), null);
    }
}
