package be.myrmacta.domain.precheck.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.precheck.model.PrecheckSpec.Condition;
import be.myrmacta.domain.precheck.model.PrecheckSpec.PieceRequirement;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** TODO §106 (lot B, D-PRECHECK-SPEC) — grammaire des attributs {@code flowable:precheck*}. */
class PrecheckSpecTest {

    private static PrecheckSpec parse(String... kv) {
        Map<String, String> attributes = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            attributes.put(kv[i], kv[i + 1]);
        }
        return PrecheckSpec.parse(attributes, "check");
    }

    @Test
    void parsesAFullSpecification() {
        PrecheckSpec spec = parse(
                PrecheckSpec.PIECES, "preuveDomicile : obligatoire, lire\n  carteGrise: lire\n\npermis : obligatoire",
                PrecheckSpec.FIELDS, "commune, plaque",
                PrecheckSpec.QUESTIONS, "adresseConforme = L'adresse de la preuve est-elle celle du formulaire ?",
                PrecheckSpec.RULES, "variable:nbVehicules > 2 => INCOHERENCE : Plus de deux véhicules.",
                PrecheckSpec.RESULT, "  precheck  ");

        assertThat(spec.pieces()).containsExactly(
                new PieceRequirement("preuveDomicile", true, true),
                new PieceRequirement("carteGrise", false, true),
                new PieceRequirement("permis", true, false));
        assertThat(spec.readPieces()).extracting(PieceRequirement::key).containsExactly("preuveDomicile", "carteGrise");
        assertThat(spec.fields()).containsExactly("commune", "plaque");
        assertThat(spec.questions()).singleElement().satisfies(q -> {
            assertThat(q.key()).isEqualTo("adresseConforme");
            assertThat(q.text()).isEqualTo("L'adresse de la preuve est-elle celle du formulaire ?");
        });
        assertThat(spec.rules()).singleElement().satisfies(r -> {
            assertThat(r.variable()).isEqualTo("nbVehicules");
            assertThat(r.kind()).isEqualTo(FindingKind.INCONSISTENT);
            assertThat(r.condition().operator()).isEqualTo(Condition.Operator.GREATER);
            assertThat(r.condition().number()).isEqualByComparingTo("2");
            assertThat(r.message()).isEqualTo("Plus de deux véhicules.");
        });
        assertThat(spec.resultVariable()).isEqualTo("precheck");
        assertThat(spec.needsModel()).isTrue();
    }

    @Test
    void requiredOnlyPiecesAndRulesNeedNoModel() {
        PrecheckSpec spec = parse(PrecheckSpec.PIECES, "permis : obligatoire",
                PrecheckSpec.RULES, "variable:ok est faux => A_VERIFIER : Contrôle à refaire.");
        assertThat(spec.needsModel()).isFalse();
        assertThat(spec.rules().getFirst().kind()).isEqualTo(FindingKind.TO_CHECK);
        assertThat(spec.resultVariable()).isNull();
    }

    @Test
    void anEmptySpecificationIsRefused() {
        assertThatThrownBy(() -> parse(PrecheckSpec.FIELDS, "commune"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BPMN activity check")
                .hasMessageContaining("at least one piece, question or rule");
    }

    @Test
    void pieceLinesAreChecked() {
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "preuve"))
                .hasMessageContaining("flowable:precheckPieces line 1").hasMessageContaining("expected");
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "Preuve : lire"))
                .hasMessageContaining("invalid piece type key");
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "preuve : lire\npreuve : obligatoire"))
                .hasMessageContaining("line 2").hasMessageContaining("duplicate piece type key");
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "preuve : ecrire"))
                .hasMessageContaining("unknown flag 'ecrire'");
    }

    @Test
    void pieceCountsAreBounded() {
        String thirtyOne = IntStream.rangeClosed(1, 31).mapToObj(i -> "p" + i + " : obligatoire")
                .collect(Collectors.joining("\n"));
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, thirtyOne)).hasMessageContaining("at most 30 lines");
        String elevenRead = IntStream.rangeClosed(1, 11).mapToObj(i -> "p" + i + " : lire")
                .collect(Collectors.joining("\n"));
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, elevenRead)).hasMessageContaining("at most 10 piece types to read");
    }

    @Test
    void fieldsAreNamesWithoutDuplicates() {
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "p : lire", PrecheckSpec.FIELDS, "a, a"))
                .hasMessageContaining("duplicate field 'a'");
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "p : lire", PrecheckSpec.FIELDS, "a, ${x}"))
                .hasMessageContaining("invalid field name");
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "p : lire", PrecheckSpec.FIELDS, "a,,b"))
                .hasMessageContaining("invalid field name ''");
        String thirtyOne = IntStream.rangeClosed(1, 31).mapToObj(i -> "f" + i).collect(Collectors.joining(","));
        assertThatThrownBy(() -> parse(PrecheckSpec.PIECES, "p : lire", PrecheckSpec.FIELDS, thirtyOne))
                .hasMessageContaining("at most 30 fields");
    }

    @Test
    void questionsAreChecked() {
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, "sans signe égal"))
                .hasMessageContaining("expected 'key = question'");
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, "q = "))
                .hasMessageContaining("expected 'key = question'");
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, "a = Une ?\na = Deux ?"))
                .hasMessageContaining("duplicate question key 'a'");
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, "1a = Une ?"))
                .hasMessageContaining("invalid question key");
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, "a = " + "x".repeat(301)))
                .hasMessageContaining("exceeds 300 characters");
        String twentyOne = IntStream.rangeClosed(1, 21).mapToObj(i -> "q" + i + " = Question ?")
                .collect(Collectors.joining("\n"));
        assertThatThrownBy(() -> parse(PrecheckSpec.QUESTIONS, twentyOne)).hasMessageContaining("at most 20 questions");
    }

    @Test
    void rulesAreChecked() {
        assertThatThrownBy(() -> parse(PrecheckSpec.RULES, "nb > 2 => MANQUANT : x"))
                .hasMessageContaining("expected 'variable:<name>");
        assertThatThrownBy(() -> parse(PrecheckSpec.RULES, "variable:nb > 2 => GRAVE : x"))
                .hasMessageContaining("expected 'variable:<name>");
        assertThatThrownBy(() -> parse(PrecheckSpec.RULES, "variable:nb contient 2 => MANQUANT : x"))
                .hasMessageContaining("unknown condition 'contient 2'");
        assertThatThrownBy(() -> parse(PrecheckSpec.RULES, "variable:nb > 2 => MANQUANT : " + "x".repeat(201)))
                .hasMessageContaining("exceeds 200 characters");
        String eleven = IntStream.rangeClosed(1, 11).mapToObj(i -> "variable:v" + i + " est vrai => MANQUANT : m")
                .collect(Collectors.joining("\n"));
        assertThatThrownBy(() -> parse(PrecheckSpec.RULES, eleven)).hasMessageContaining("at most 10 rules");
    }

    @Test
    void everyConditionIsParsed() {
        PrecheckSpec spec = parse(PrecheckSpec.RULES, String.join("\n",
                "variable:a est vrai => MANQUANT : a",
                "variable:b est faux => MANQUANT : b",
                "variable:c est vide => MANQUANT : c",
                "variable:d n'est pas vide => MANQUANT : d",
                "variable:e n’est pas vide => MANQUANT : e",
                "variable:f < -1.5 => MANQUANT : f",
                "variable:g = 3 => MANQUANT : g",
                "variable:h = \"Liège\" => MANQUANT : h"));
        assertThat(spec.rules()).extracting(r -> r.condition().operator()).containsExactly(
                Condition.Operator.IS_TRUE, Condition.Operator.IS_FALSE, Condition.Operator.IS_EMPTY,
                Condition.Operator.IS_NOT_EMPTY, Condition.Operator.IS_NOT_EMPTY, Condition.Operator.LESS,
                Condition.Operator.EQUALS_NUMBER, Condition.Operator.EQUALS_TEXT);
        assertThat(spec.rules().get(5).condition().number()).isEqualByComparingTo("-1.5");
        assertThat(spec.rules().get(7).condition().text()).isEqualTo("Liège");
    }

    @Test
    void conditionsNeverMatchAnotherType() {
        Condition greater = new Condition(Condition.Operator.GREATER, BigDecimal.TWO, null);
        Condition less = new Condition(Condition.Operator.LESS, BigDecimal.TWO, null);
        Condition equals = new Condition(Condition.Operator.EQUALS_NUMBER, BigDecimal.TWO, null);
        assertThat(greater.test(3)).isTrue();
        assertThat(greater.test(" 3 ")).isTrue();
        assertThat(greater.test(2L)).isFalse();
        assertThat(less.test(1.5d)).isTrue();
        assertThat(equals.test(2.0d)).isTrue();
        for (Object other : new Object[] {null, "abc", true, List.of(1), Double.NaN, Double.NEGATIVE_INFINITY, Float.NaN}) {
            assertThat(greater.test(other)).as("> 2 on %s", other).isFalse();
            assertThat(less.test(other)).as("< 2 on %s", other).isFalse();
            assertThat(equals.test(other)).as("= 2 on %s", other).isFalse();
        }

        Condition isTrue = new Condition(Condition.Operator.IS_TRUE, null, null);
        assertThat(isTrue.test(true)).isTrue();
        assertThat(isTrue.test("true")).isTrue();
        assertThat(isTrue.test(1)).isFalse();
        Condition isFalse = new Condition(Condition.Operator.IS_FALSE, null, null);
        assertThat(isFalse.test(false)).isTrue();
        assertThat(isFalse.test(null)).isFalse();

        Condition empty = new Condition(Condition.Operator.IS_EMPTY, null, null);
        assertThat(empty.test(null)).isTrue();
        assertThat(empty.test("  ")).isTrue();
        assertThat(empty.test(List.of())).isTrue();
        assertThat(empty.test(Map.of())).isTrue();
        assertThat(empty.test(0)).isFalse();
        assertThat(new Condition(Condition.Operator.IS_NOT_EMPTY, null, null).test("x")).isTrue();

        Condition text = new Condition(Condition.Operator.EQUALS_TEXT, null, "Liège");
        assertThat(text.test(" Liège ")).isTrue();
        assertThat(text.test("liège")).isFalse();
        assertThat(text.test(null)).isFalse();
    }

    @Test
    void aPieceRequirementIsRequiredOrRead() {
        assertThatThrownBy(() -> new PieceRequirement("p", false, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
