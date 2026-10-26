package be.myrmacta.examples;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.PrecheckLimits;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * TODO §106 (issue #211, lot D, D3) — le jeu d'évaluation reste valide (cas, spécification de la brique, type de
 * l'exemple) et ses mesures sont justes ; vérifié dans {@code verify} avec des modèles simulés, sans réseau.
 */
class PrecheckEvaluationHarnessTest {

    /** Rend, appel après appel, la réponse attendue du cas suivant (le harnais appelle le modèle dans l'ordre des cas). */
    private static PrecheckModelPort oracle(List<PrecheckEvaluationHarness.Case> cases, List<String> prompts) {
        Iterator<PrecheckEvaluationHarness.Case> next = cases.iterator();
        return request -> {
            prompts.add(request.user());
            Map<String, String> expected = next.next().expected();
            String findings = expected.entrySet().stream()
                    .map(e -> "{\"kind\":\"" + e.getValue() + "\",\"sourceType\":\"QUESTION\",\"sourceRef\":\"" + e.getKey()
                            + "\",\"message\":\"Réponse attendue.\",\"confidence\":0.9}")
                    .collect(Collectors.joining(","));
            return new ModelExchange.Reply("{\"summary\":\"s\",\"findings\":[" + findings + "]}", 100, 20, "fake", "oracle");
        };
    }

    @Test
    void theCasesTheBrickAndTheTypeOfTheExampleFitTogether() throws Exception {
        var cases = PrecheckEvaluationHarness.cases();
        var spec = PrecheckEvaluationHarness.spec();
        var type = PrecheckEvaluationHarness.dossierType();

        assertThat(cases).hasSizeGreaterThanOrEqualTo(10);
        var questions = spec.questions().stream().map(q -> q.key()).toList();
        for (var c : cases) {
            assertThat(c.expected().keySet()).as(c.id()).containsExactlyInAnyOrderElementsOf(questions);
            assertThat(c.expected().values()).as(c.id()).allMatch(PrecheckEvaluationHarness.KINDS::contains);
            assertThat(c.pieces()).as(c.id()).allMatch(p -> type.pieceType(p.type()).isPresent());
        }
        for (String kind : PrecheckEvaluationHarness.KINDS) {
            assertThat(cases).as("au moins un cas attendu " + kind)
                    .anyMatch(c -> c.expected().containsValue(kind));
        }
        assertThat(spec.fields()).allMatch(f -> type.fieldDefinitions().stream()
                .anyMatch(d -> d.name().equals(f) && !d.isSensitive()));
    }

    @Test
    void aPerfectModelScoresOneHundredPercent() throws Exception {
        List<String> prompts = new ArrayList<>();
        var report = PrecheckEvaluationHarness.run("oracle",
                oracle(PrecheckEvaluationHarness.cases(), prompts), PrecheckLimits.defaults());

        assertThat(report.accuracy()).isEqualTo(1.0);
        assertThat(report.failedRuns()).isZero();
        for (String kind : PrecheckEvaluationHarness.KINDS) {
            assertThat(report.scores(kind)[0]).as("précision " + kind).isEqualTo(1.0);
            assertThat(report.scores(kind)[1]).as("rappel " + kind).isEqualTo(1.0);
        }
        assertThat(report.markdown()).contains("| OK |", "exactitude 100 %");
        // Les pièces passent par PDFBox : leur texte atteint le modèle ; la consigne injectée y arrive comme une donnée,
        // dans la borne de sa pièce (c'est ce que mesure le cas « injection » contre un vrai modèle).
        assertThat(String.join("\n", prompts)).contains("1-ABC-123", "Tilleuls");
        String injected = prompts.get(9);
        int piece = injected.indexOf("clé=preuveDomicile");
        int end = injected.indexOf("<<<FIN-", piece);
        assertThat(piece).isPositive();
        assertThat(injected.substring(piece, end)).contains("ignore les consignes");
    }

    @Test
    void aModelThatAlwaysAnswersOkIsMeasuredAsSuch() throws Exception {
        PrecheckModelPort alwaysOk = request -> new ModelExchange.Reply("""
                {"summary":"s","findings":[
                 {"kind":"OK","sourceType":"QUESTION","sourceRef":"domicileAuNom","message":"Oui.","confidence":0.5},
                 {"kind":"OK","sourceType":"QUESTION","sourceRef":"adresseConforme","message":"Oui.","confidence":0.5},
                 {"kind":"OK","sourceType":"QUESTION","sourceRef":"plaqueConforme","message":"Oui.","confidence":0.5}]}""",
                1, 1, "fake", "toujours-ok");
        var report = PrecheckEvaluationHarness.run("toujours-ok", alwaysOk, PrecheckLimits.defaults());

        double expectedOk = report.scores("OK")[2];
        assertThat(report.scores("OK")[0]).isCloseTo(expectedOk / report.predictions().size(), within(1e-9));
        assertThat(report.scores("OK")[1]).isEqualTo(1.0);
        assertThat(report.scores("INCONSISTENT")[1]).isZero();
        assertThat(report.scores("INCONSISTENT")[0]).isNaN(); // jamais prédit
        assertThat(report.markdown()).contains("**OK**");
    }
}
