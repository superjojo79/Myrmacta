package com.flowforge.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §102 (issue #184) — drapeau « valeur unique » sur un champ de dossier : règles U2, U3, U4 et U13. */
class FieldUniqueModelTest {

    private static FieldDefinition field(String name, FieldType type, boolean sensitive, String formula, boolean unique) {
        return new FieldDefinition(name, name, type, false, null, null, null, sensitive, null, null, null, formula, null, null,
                false, null, null, unique);
    }

    private static FieldDefinition field(FieldType type, boolean unique) {
        return field("f", type, false, null, unique);
    }

    @Test
    void onlyTextNumberAndDateCanBeUnique_U2() {
        for (FieldType ok : List.of(FieldType.STRING, FieldType.NUMBER, FieldType.DATE)) {
            assertThat(field(ok, true).isUnique()).isTrue();
        }
        for (FieldType ko : List.of(FieldType.BOOLEAN, FieldType.MONEY, FieldType.EXTERNAL_REF, FieldType.FILE)) {
            assertThatThrownBy(() -> field(ko, true)).hasMessageContaining("cannot be unique");
        }
        assertThatThrownBy(() -> new FieldDefinition("s", "s", FieldType.SELECT, false, null, List.of("A", "B"), null, false,
                null, null, null, null, null, null, false, null, null, true)).hasMessageContaining("cannot be unique");
    }

    @Test
    void aSensitiveFieldCannotBeUnique_U3() {
        assertThatThrownBy(() -> field("f", FieldType.STRING, true, null, true)).hasMessageContaining("sensitive");
    }

    @Test
    void aComputedFieldCanBeUnique_U4() {
        assertThat(field("cle", FieldType.STRING, false, "dossier.fields.a & \"-\"", true).isUnique()).isTrue();
    }

    @Test
    void aGroupAndItsSubFieldsAreNeverUnique() {
        assertThatThrownBy(() -> new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(FieldType.STRING, true)), null, null)).hasMessageContaining("cannot be unique");
        assertThatThrownBy(() -> new FieldDefinition("g", "g", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(FieldType.STRING, false)), null, null, null, null, null, false, null, null, true))
                .hasMessageContaining("cannot be unique");
    }

    @Test
    void aUniqueFieldNameIsAtMostOneHundredCharacters_U13() {
        assertThat(field("n".repeat(100), FieldType.STRING, false, null, true).isUnique()).isTrue();
        assertThatThrownBy(() -> field("n".repeat(101), FieldType.STRING, false, null, true))
                .hasMessageContaining("at most 100");
        assertThat(field("n".repeat(101), FieldType.STRING, false, null, false).isUnique()).isFalse();
    }

    @Test
    void uniqueDefaultsToFalseAndSurvivesCopies() {
        assertThat(new FieldDefinition("a", "a", FieldType.STRING, false, null, null, null, false).isUnique()).isFalse();
        assertThat(field(FieldType.STRING, false).unique()).isFalse();
        FieldDefinition u = field(FieldType.STRING, true);
        assertThat(u.withRequired(true).isUnique()).isTrue();
        assertThat(u.withHidden().isUnique()).isTrue();
    }

    /** Un appelant MCP ne reçoit que le message : il nomme champ, code et détenteur, jamais la valeur saisie. */
    @Test
    void theValidationMessageNamesFieldCodeAndHolderButNotTheValue() {
        var ex = new DossierFieldValidationException(List.of(FieldValidationError.notUnique("plaque", "DOS-1"),
                FieldValidationError.required("nom")));
        assertThat(ex.getMessage()).isEqualTo("Dossier field validation failed: plaque UNIQUE (Value already used by dossier "
                + "DOS-1); nom REQUIRED (Field is required)");
        assertThat(ex.errors().getFirst().reference()).isEqualTo("DOS-1");
        assertThat(new DossierFieldValidationException(List.of()).getMessage()).isEqualTo("Dossier field validation failed");
    }
}
