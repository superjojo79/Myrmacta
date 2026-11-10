package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §126 (issue #9) — drapeau managedByWorkflow : contraintes de schéma (D-MANAGED-SCHEMA). */
class FieldManagedByWorkflowModelTest {

    private static FieldDefinition managed(
            boolean required, String defaultValue, String formula, String requiredWhen, boolean managed) {
        return new FieldDefinition(
                "idTech",
                "Identifiant",
                FieldType.STRING,
                required,
                defaultValue,
                null,
                null,
                false,
                null,
                null,
                null,
                formula,
                null,
                requiredWhen,
                false,
                null,
                null,
                false,
                managed);
    }

    @Test
    void managedByWorkflowDefaultsToFalseAndCanBeSetOnAPlainField() {
        assertThat(new FieldDefinition("a", "A", FieldType.STRING, false, null, null, null, false).isManagedByWorkflow())
                .isFalse();
        assertThat(managed(false, null, null, null, true).isManagedByWorkflow()).isTrue();
        assertThat(managed(false, null, null, null, false).managedByWorkflow()).isFalse();
    }

    @Test
    void managedFieldCannotBeRequiredOrCarryADefaultOrFormulaOrRequiredWhen() {
        assertThatThrownBy(() -> managed(true, null, null, null, true)).hasMessageContaining("managedByWorkflow");
        assertThatThrownBy(() -> managed(false, "x", null, null, true)).hasMessageContaining("managedByWorkflow");
        assertThatThrownBy(() -> managed(false, null, "1", null, true)).hasMessageContaining("managedByWorkflow");
        assertThatThrownBy(() -> managed(false, null, null, "true", true)).hasMessageContaining("managedByWorkflow");
    }

    @Test
    void managedFieldMayBeSensitiveOrUniqueOrSearchableOrVisibleWhen() {
        FieldDefinition sensitive = new FieldDefinition(
                "secret", "Secret", FieldType.STRING, false, null, null, null, true, null, null, null, null, null, null,
                false, null, null, false, true);
        assertThat(sensitive.isManagedByWorkflow()).isTrue();
        assertThat(sensitive.isSensitive()).isTrue();

        FieldDefinition unique = new FieldDefinition(
                "mat", "Mat", FieldType.STRING, false, null, null, null, false, null, null, null, null, null, null, false,
                null, null, true, true);
        assertThat(unique.isUnique()).isTrue();

        FieldDefinition searchable = new FieldDefinition(
                "mat", "Mat", FieldType.STRING, false, null, null, null, false, null, null, null, null, null, null, true,
                null, null, false, true);
        assertThat(searchable.isSearchable()).isTrue();

        FieldDefinition visible = new FieldDefinition(
                "id", "Id", FieldType.STRING, false, null, null, null, false, null, null, null, null,
                "dossier.fields.ok = true", null, false, null, null, false, true);
        assertThat(visible.visibleWhen()).isEqualTo("dossier.fields.ok = true");
    }

    @Test
    void aGroupSubFieldCannotBeManagedByWorkflow() {
        FieldDefinition sub = managed(false, null, null, null, true);
        assertThatThrownBy(() -> new FieldDefinition(
                        "g",
                        "G",
                        FieldType.REPEATABLE_GROUP,
                        false,
                        null,
                        null,
                        null,
                        false,
                        List.of(sub),
                        null,
                        null))
                .hasMessageContaining("managedByWorkflow");
    }

    @Test
    void copiesPreserveTheFlag() {
        FieldDefinition m = managed(false, null, null, null, true);
        assertThat(m.withRequired(false).isManagedByWorkflow()).isTrue();
        assertThat(m.withHidden().isManagedByWorkflow()).isTrue();
    }

    @Test
    void validationErrorCodeIsStableAndDoesNotEchoTheValue() {
        FieldValidationError err = FieldValidationError.managedByWorkflow("identifiantIam");
        assertThat(err.code()).isEqualTo("MANAGED_BY_WORKFLOW");
        assertThat(err.field()).isEqualTo("identifiantIam");
        assertThat(err.message()).doesNotContain("forged-value");
    }
}
