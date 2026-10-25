package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** TODO §80 — contraintes structurelles des règles portées par une définition de champ. */
class FieldDefinitionRulesTest {

    private static FieldDefinition def(FieldType type, boolean required, String defaultValue, boolean sensitive, String formula,
            String visibleWhen, String requiredWhen) {
        return new FieldDefinition("f", "F", type, required, defaultValue, null, null, sensitive, null, null, null, formula, visibleWhen, requiredWhen);
    }

    @Test
    void blankExpressionsAreNormalizedToNullAndTheLegacyConstructorsCarryNoRules() {
        FieldDefinition d = def(FieldType.NUMBER, false, null, false, "  ", "", " ");
        assertThat(d.formula()).isNull();
        assertThat(d.visibleWhen()).isNull();
        assertThat(d.requiredWhen()).isNull();
        assertThat(d.hasFormula()).isFalse();
        assertThat(new FieldDefinition("f", "F", FieldType.STRING, false, null, null, null, false).formula()).isNull();
    }

    @Test
    void aComputedFieldIsReadOnlyAndNeverSensitiveNorAGroupNorAFile() {
        assertThat(def(FieldType.NUMBER, false, null, false, "1", null, null).hasFormula()).isTrue();
        assertThatThrownBy(() -> def(FieldType.NUMBER, true, null, false, "1", null, null)).hasMessageContaining("computed");
        assertThatThrownBy(() -> def(FieldType.NUMBER, false, "5", false, "1", null, null)).hasMessageContaining("computed");
        assertThatThrownBy(() -> def(FieldType.NUMBER, false, null, true, "1", null, null)).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> def(FieldType.NUMBER, false, null, false, "1", "true", null)).hasMessageContaining("computed");
        assertThatThrownBy(() -> def(FieldType.NUMBER, false, null, false, "1", null, "true")).hasMessageContaining("computed");
        assertThatThrownBy(() -> def(FieldType.FILE, false, null, false, "1", null, null)).hasMessageContaining("FILE");
    }

    @Test
    void anExpressionIsBounded() {
        String tooLong = "1 + ".repeat(600) + "1";
        assertThatThrownBy(() -> def(FieldType.NUMBER, false, null, false, tooLong, null, null)).hasMessageContaining("exceeds");
    }

    @Test
    void aGroupSubFieldCannotCarryRules() {
        FieldDefinition sub = def(FieldType.STRING, false, null, false, null, "true", null);
        assertThatThrownBy(() -> new FieldDefinition("g", "G", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(new FieldDefinition("s", "S", FieldType.STRING, false, null, null, null, false, null, null, null, null, "true", null)), null, null))
                .hasMessageContaining("sub-field");
        assertThat(sub.visibleWhen()).isEqualTo("true");
    }

    @Test
    void withRequiredKeepsEverythingElse() {
        FieldDefinition d = def(FieldType.STRING, false, null, false, null, "true", "false");
        FieldDefinition r = d.withRequired(true);
        assertThat(r.required()).isTrue();
        assertThat(r.visibleWhen()).isEqualTo("true");
        assertThat(r.requiredWhen()).isEqualTo("false");
    }

    @Test
    void theToStringNeverPrintsTheExpressionsOrTheDefaultValue() {
        FieldDefinition d = def(FieldType.STRING, false, "secret", false, null, "dossier.fields.confidentiel", null);
        assertThat(d.toString()).doesNotContain("secret").doesNotContain("confidentiel").contains("visibleWhen=true");
    }
}
