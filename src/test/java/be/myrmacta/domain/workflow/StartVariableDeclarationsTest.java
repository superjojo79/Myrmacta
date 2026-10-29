package be.myrmacta.domain.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.workflow.StartVariableDeclarations.Raw;
import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** TODO §111 (issue #187, D-START-DECLARED). */
class StartVariableDeclarationsTest {

    @Test
    void parseKeepsOrderAndStripsTheNameAndLabel() {
        List<StartVariableDeclaration> declarations = StartVariableDeclarations.parse(List.of(
                new Raw(" motif ", " Motif de la demande ", "true"),
                new Raw("note", "Note", "FALSE")));

        assertThat(declarations).containsExactly(
                new StartVariableDeclaration("motif", "Motif de la demande", true),
                new StartVariableDeclaration("note", "Note", false));
    }

    @Test
    void parseRejectsAReservedDuplicateOrMalformedDeclarationWithoutEchoingTheLabel() {
        assertThatThrownBy(() -> StartVariableDeclarations.parse(List.of(new Raw("dossierId", "Dossier", "true"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved")
                .hasMessageContaining("dossierId");
        assertThatThrownBy(() -> StartVariableDeclarations.parse(List.of(
                        new Raw("motif", "Motif", "true"), new Raw("motif", "Autre", "false"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicated");
        assertThatThrownBy(() -> StartVariableDeclarations.parse(List.of(new Raw("1bad", "Libellé", "true"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name")
                .hasMessageNotContaining("1bad");
        assertThatThrownBy(() -> StartVariableDeclarations.parse(List.of(new Raw("motif", "Motif", "maybe"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
        String longLabel = "x".repeat(201);
        assertThatThrownBy(() -> StartVariableDeclarations.parse(List.of(new Raw("motif", longLabel, "true"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(longLabel);
    }

    @Test
    void anEmptyDeclarationListLeavesTheCallerMapUntouched() {
        Map<String, Object> input = Map.of("priorite", "haute");

        assertThat(StartVariableDeclarations.bind(List.of(), input)).isSameAs(input);
        assertThat(StartVariableDeclarations.bind(null, null)).isEmpty();
    }

    @Test
    void bindKeepsOnlyDeclaredTextAndOmitsABlankOptional() {
        List<StartVariableDeclaration> declarations = List.of(
                new StartVariableDeclaration("motif", "Motif", true),
                new StartVariableDeclaration("note", "Note", false));

        assertThat(StartVariableDeclarations.bind(declarations, Map.of("motif", "  travaux  ", "note", "  ")))
                .containsExactly(Map.entry("motif", "travaux"));
    }

    @Test
    void bindRejectsAMissingRequiredAnUnknownKeyOrANonTextValueWithoutTheValue() {
        List<StartVariableDeclaration> declarations = List.of(new StartVariableDeclaration("motif", "Motif", true));

        assertThatThrownBy(() -> StartVariableDeclarations.bind(declarations, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required")
                .hasMessageContaining("motif");
        assertThatThrownBy(() -> StartVariableDeclarations.bind(declarations, Map.of("motif", "ok", "autre", "secrète")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not declared")
                .hasMessageContaining("autre")
                .hasMessageNotContaining("secrète");
        assertThatThrownBy(() -> StartVariableDeclarations.bind(declarations, Map.of("motif", 12)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not text")
                .hasMessageNotContaining("12");
    }
}
