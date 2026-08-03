package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FieldDefinitionTest {

    @Test
    void sensitiveTrue_isPreserved() {
        var def = new FieldDefinition(
                "ssn", "N° sécu", FieldType.STRING, false, null, List.of(), null, true);
        assertThat(def.sensitive()).isTrue();
        assertThat(def.isSensitive()).isTrue();
    }

    @Test
    void convenienceConstructor_defaultsSensitiveToFalse() {
        var def = new FieldDefinition(
                "name", "Name", FieldType.STRING, true, null, List.of(), "identity");
        assertThat(def.sensitive()).isFalse();
        assertThat(def.isSensitive()).isFalse();
    }

    @Test
    void nullSensitive_coercedToFalse() {
        var def = new FieldDefinition(
                "name", "Name", FieldType.STRING, false, null, List.of(), null, null);
        assertThat(def.sensitive()).isFalse();
    }
}
