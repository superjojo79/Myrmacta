package be.myrmacta.domain.dossier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** TODO §85 (LC4a) — mise en page d'un champ : aide contextuelle et largeur. */
class FieldLayoutTest {

    private static FieldDefinition field(String helpText, String width) {
        return new FieldDefinition("f", "F", FieldType.STRING, false, null, null, "Section", false, null, null, null, null,
                null, null, false, helpText, width);
    }

    @Test
    void fullWidthIsTheDefaultAndStoresNothing() {
        assertThat(field(null, null).width()).isNull();
        assertThat(field(null, "full").width()).isNull();
        assertThat(field(null, "  ").width()).isNull();
        assertThat(field(null, "half").width()).isEqualTo("HALF");
    }

    @Test
    void anUnknownWidthIsRefused() {
        assertThatThrownBy(() -> field(null, "THIRD")).hasMessageContaining("width");
    }

    @Test
    void helpTextIsBlankToNullBoundedAndPlain() {
        assertThat(field("   ", null).helpText()).isNull();
        assertThat(field("Ligne 1\nLigne 2", null).helpText()).isEqualTo("Ligne 1\nLigne 2");
        assertThat(field("x".repeat(FieldDefinition.MAX_HELP_TEXT_CHARS), null).helpText()).isNotNull();
        assertThatThrownBy(() -> field("x".repeat(FieldDefinition.MAX_HELP_TEXT_CHARS + 1), null)).hasMessageContaining("helpText");
        assertThatThrownBy(() -> field("a\u0000b", null)).hasMessageContaining("control");
        assertThatThrownBy(() -> field("a\u001bb", null)).hasMessageContaining("control");
    }

    /** Revue adversariale LC4a (Moyen) : une aide de sous-champ n'est affichée nulle part — refusée plutôt que stockée en silence. */
    @Test
    void aSubFieldHasNeitherHelpNorWidth() {
        assertThatThrownBy(() -> new FieldDefinition("g", "G", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field("aide", null)), null, null)).hasMessageContaining("neither width nor help");
        assertThatThrownBy(() -> new FieldDefinition("g", "G", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(null, "HALF")), null, null)).hasMessageContaining("neither width nor help");
        assertThat(new FieldDefinition("g", "G", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(null, null)), null, null).fields()).hasSize(1);
    }

    @Test
    void aRepeatableGroupIsAlwaysFullWidth() {
        assertThatThrownBy(() -> new FieldDefinition("g", "G", FieldType.REPEATABLE_GROUP, false, null, null, null, false,
                List.of(field(null, null)), null, null, null, null, null, false, null, "HALF"))
                .hasMessageContaining("full width");
    }

    @Test
    void bidirectionalAndZeroWidthCharactersAreRefusedInHelpText() {
        for (String bad : new String[] {"a\u202Eb", "a\u200Bb", "a\u2066b", "a\uFEFFb"}) {
            assertThatThrownBy(() -> field(bad, null)).as(bad).hasMessageContaining("bidirectional");
        }
    }

    @Test
    void layoutSurvivesCopiesAndTheJsonRoundTrip() throws Exception {
        FieldDefinition original = field("Aide", "HALF");
        assertThat(original.withRequired(true).helpText()).isEqualTo("Aide");
        assertThat(original.withHidden().width()).isEqualTo("HALF");

        JsonMapper mapper = JsonMapper.builder().build();
        FieldDefinition back = mapper.readValue(mapper.writeValueAsString(original), FieldDefinition.class);
        assertThat(back).isEqualTo(original);
        // Une définition enregistrée avant LC4a (sans ces clés) se relit sans erreur.
        FieldDefinition legacy = mapper.readValue("{\"name\":\"a\",\"label\":\"A\",\"type\":\"STRING\",\"required\":false}", FieldDefinition.class);
        assertThat(legacy.helpText()).isNull();
        assertThat(legacy.width()).isNull();
    }
}
