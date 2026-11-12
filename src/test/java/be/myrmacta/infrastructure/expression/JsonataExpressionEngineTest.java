package be.myrmacta.infrastructure.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dashjoin.jsonata.AstInspector;
import be.myrmacta.domain.expression.model.ExpressionEvaluationException;
import be.myrmacta.domain.expression.model.ExpressionLimits;
import be.myrmacta.domain.expression.model.ExpressionResult;
import be.myrmacta.domain.expression.model.InvalidExpressionException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** LC2 (TODO §75) — contrat du moteur JSONata borné : fonctionnel, puis adversarial. */
class JsonataExpressionEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-30T10:15:30Z"), ZoneOffset.UTC);
    private final JsonataExpressionEngine engine = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS, CLOCK);

    private static Map<String, Object> dossier(Object montant, String pays) {
        return Map.of("dossier", Map.of("reference", "DOS-1", "fields", Map.of("montant", montant, "pays", pays)));
    }

    // ---- fonctionnel -------------------------------------------------------------------------------------------

    /** TODO §134 (D-EXPR-ASCII) — login : accents retirés, minuscules, hors `[a-z0-9.-]` supprimé. */
    @Test
    void asciiStripsAccentsAndKeepsOnlyLoginCharacters() {
        assertThat(engine.evaluate("$ascii($substring(\"Élise\", 0, 1) & \"Van den Broeck\")", Map.of()).value())
                .isEqualTo("evandenbroeck");
        assertThat(engine.evaluate("$ascii(\"Jean-Pierre.2\")", Map.of()).value()).isEqualTo("jean-pierre.2");
        assertThat(engine.evaluate("$ascii(\"O'Neil\")", Map.of()).value()).isEqualTo("oneil");
        assertThat(engine.evaluate("$ascii(\"ß\")", Map.of()).value()).isEqualTo("ss");
        assertThat(engine.evaluate("$ascii(\"Ø\")", Map.of()).value()).isEqualTo("o");
        assertThat(engine.evaluate("$ascii(\"Ł\")", Map.of()).value()).isEqualTo("l");
        assertThat(engine.evaluate("$ascii(\"œ\")", Map.of()).value()).isEqualTo("oe");
        assertThat(engine.evaluate("$ascii(\"æ\")", Map.of()).value()).isEqualTo("ae");
        assertThat(engine.evaluate("$ascii(\"e\u0301\")", Map.of()).value()).isEqualTo("e");
        assertThat(engine.evaluate("$ascii(null)", Map.of()).defined()).isFalse();
        assertThatThrownBy(() -> engine.evaluate("$ascii(\"\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("no login characters");
        assertThatThrownBy(() -> engine.evaluate("$ascii(\"!!!\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("no login characters");
        assertThatThrownBy(() -> engine.evaluate("$ascii(1)", Map.of("secret", "TOPSECRET")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("expects text")
                .hasMessageNotContaining("TOPSECRET");
    }

    @Test
    void conditionOverDossierFieldsIsABoolean() {
        ExpressionResult r = engine.evaluate(
                "dossier.fields.montant > 5000 and dossier.fields.pays = \"FR\"", dossier(6000, "FR"));
        assertThat(r.defined()).isTrue();
        assertThat(r.value()).isEqualTo(Boolean.TRUE);
        assertThat(engine.evaluate("dossier.fields.montant > 5000 and dossier.fields.pays = \"FR\"", dossier(6000, "BE"))
                        .value())
                .isEqualTo(Boolean.FALSE);
    }

    @Test
    void integralResultsAreLongsAndOthersDoubles() {
        assertThat(engine.evaluate("dossier.fields.montant * 0.2", dossier(6000, "FR")).value()).isEqualTo(1200L);
        assertThat(engine.evaluate("dossier.fields.montant / 7", dossier(10, "FR")).value()).isInstanceOf(Double.class);
    }

    @Test
    void mappingOfAConnectorResultBuildsANewJsonObject() {
        Map<String, Object> ctx = Map.of("connectorResult", Map.of(
                "data", Map.of("score", 712, "name", "Acme"),
                "items", List.of(Map.of("id", "a"), Map.of("id", "b"))));
        ExpressionResult r = engine.evaluate(
                "{ \"score\": connectorResult.data.score, \"name\": $uppercase(connectorResult.data.name),"
                        + " \"ids\": connectorResult.items.id }",
                ctx);
        assertThat(r.value()).isEqualTo(Map.of("score", 712L, "name", "ACME", "ids", List.of("a", "b")));
    }

    @Test
    void lambdasAndHigherOrderFunctionsAreAvailable() {
        Map<String, Object> ctx = Map.of("lines", List.of(Map.of("qty", 2, "price", 10), Map.of("qty", 3, "price", 5)));
        assertThat(engine.evaluate("$sum($map(lines, function($l){ $l.qty * $l.price }))", ctx).value())
                .isEqualTo(35L);
    }

    /** Les exemples du guide de conception (§3sexies) doivent rester exécutables tels qu'écrits. */
    @Test
    void theExamplesOfTheDesignGuideRunAsWritten() {
        Map<String, Object> ctx = Map.of(
                "dossier", Map.of("fields", Map.of("montant", 6000, "pays", "FR", "score", 7)),
                "connectorResult", Map.of("data", Map.of("score", 712),
                        "lignes", List.of(Map.of("qty", 2, "prix", 10), Map.of("qty", 3, "prix", 5))));
        assertThat(engine.evaluate("dossier.fields.montant > 5000 and dossier.fields.pays = \"FR\"", ctx).value())
                .isEqualTo(true);
        assertThat(engine.evaluate("dossier.fields.montant * 0.2", ctx).value()).isEqualTo(1200L);
        assertThat(engine.evaluate(
                        "$exists(dossier.fields.absent) ? dossier.fields.absent : 0", ctx).value()).isEqualTo(0L);
        assertThat(engine.evaluate(
                        "$sum($map(connectorResult.lignes, function($l){ $l.qty * $l.prix }))", ctx).value())
                .isEqualTo(35L);
        assertThat(engine.evaluate("connectorResult.data.score", ctx).value()).isEqualTo(712L);
    }

    /** TODO §76 (D1) : les champs structurés se calculent avec `$number(...)` sur le montant décimal exact (texte). */
    @Test
    void structuredDossierFieldsAreUsableInExpressions() {
        Map<String, Object> ctx = Map.of("dossier", Map.of("fields", Map.of(
                "montant", Map.of("amount", "1234.50", "currency", "EUR"),
                "fournisseur", Map.of("system", "d365fo", "id", "V-1"),
                "lignes", List.of(
                        Map.of("poste", "Toiture", "prix", Map.of("amount", "1000.00", "currency", "EUR")),
                        Map.of("poste", "Cuisine", "prix", Map.of("amount", "234.50", "currency", "EUR"))))));
        assertThat(engine.evaluate("$number(dossier.fields.montant.amount) > 1000", ctx).value()).isEqualTo(true);
        assertThat(engine.evaluate("$sum(dossier.fields.lignes.$number(prix.amount))", ctx).value()).isEqualTo(1234.5d);
        assertThat(engine.evaluate("dossier.fields.fournisseur.system = \"d365fo\"", ctx).value()).isEqualTo(true);
        assertThat(engine.evaluate("$count(dossier.fields.lignes)", ctx).value()).isEqualTo(2L);
    }

    @Test
    void anAbsentPathIsUndefinedNotNull() {
        ExpressionResult r = engine.evaluate("dossier.fields.absent", dossier(1, "FR"));
        assertThat(r.defined()).isFalse();
        assertThat(r.value()).isNull();
    }

    @Test
    void anExplicitNullIsDefinedAndNull() {
        Map<String, Object> ctx = new java.util.HashMap<>();
        ctx.put("x", null);
        ExpressionResult r = engine.evaluate("x", ctx);
        assertThat(r.defined()).isTrue();
        assertThat(r.value()).isNull();
    }

    @Test
    void nowComesFromTheInjectedClockAndCannotBeOverriddenByTheContext() {
        assertThat(engine.evaluate("now", Map.of()).value()).isEqualTo("2026-09-30T10:15:30Z");
        assertThat(engine.evaluate("now", Map.of("now", "1999-01-01T00:00:00Z")).value())
                .isEqualTo("2026-09-30T10:15:30Z");
        assertThat(engine.evaluate("$toMillis(now) - $toMillis(\"2026-09-30T10:15:00Z\")", Map.of()).value())
                .isEqualTo(30_000L);
    }

    @Test
    void validateAcceptsAGoodExpressionAndLocatesASyntaxError() {
        engine.validate("dossier.fields.montant > 5000");
        assertThatThrownBy(() -> engine.validate("dossier.fields.montant >"))
                .isInstanceOf(InvalidExpressionException.class)
                .satisfies(e -> assertThat(((InvalidExpressionException) e).position()).isNotNull());
    }

    @Test
    void userRaisedErrorsSurfaceAsEvaluationFailures() {
        assertThatThrownBy(() -> engine.evaluate("$error(\"montant manquant\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("montant manquant");
    }

    @Test
    void theCallersContextIsNeverMutated() {
        Map<String, Object> fields = new java.util.LinkedHashMap<>(Map.of("a", 1));
        Map<String, Object> ctx = new java.util.LinkedHashMap<>(Map.of("fields", fields));
        engine.evaluate("$merge([fields, {\"b\": 2}])", ctx);
        assertThat(fields).containsOnlyKeys("a");
        assertThat(ctx).containsOnlyKeys("fields");
    }

    @Test
    void theEngineIsSafeUnderConcurrentUse() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int n = i;
                Callable<Object> task = () -> engine.evaluate("x * 2", Map.of("x", n)).value();
                futures.add(pool.submit(task));
            }
            for (int i = 0; i < 200; i++) {
                assertThat(futures.get(i).get()).isEqualTo((long) i * 2);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- adversarial : construction interdite ------------------------------------------------------------------

    @Test
    void blankOrOverlongExpressionsAreRefused() {
        assertThatThrownBy(() -> engine.validate("  ")).isInstanceOf(InvalidExpressionException.class);
        assertThatThrownBy(() -> engine.validate(null)).isInstanceOf(InvalidExpressionException.class);
        assertThatThrownBy(() -> engine.validate("1" + "+1".repeat(1_500))).isInstanceOf(InvalidExpressionException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void regexLiteralsAreRefusedBecauseBacktrackingCannotBeInterrupted() {
        for (String expr : List.of(
                "$match(\"aaaa\", /a+/)",
                "$replace(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\", /(a+)+$/, \"\")",
                "$split(\"a,b\", /,/)",
                "$contains(\"abc\", /b/)")) {
            assertThatThrownBy(() -> engine.validate(expr)).as(expr)
                    .isInstanceOf(InvalidExpressionException.class)
                    .hasMessageContaining("regular expression");
        }
        // Les motifs en chaîne littérale restent permis (recherche de sous-chaîne, pas d'automate).
        assertThat(engine.evaluate("$contains(\"abc\", \"b\")", Map.of()).value()).isEqualTo(Boolean.TRUE);
        assertThat(engine.evaluate("$replace(\"a-b\", \"-\", \"+\")", Map.of()).value()).isEqualTo("a+b");
        assertThat(engine.evaluate("10 / 4", Map.of()).value()).isEqualTo(2.5d); // la division n'est pas un regex
    }

    @Test
    void dangerousOrNonDeterministicFunctionsAreRefusedAtValidation() {
        for (String expr : List.of("$eval(\"1+1\")", "$random()", "$now()", "$millis()", "$eval", "$f := $eval; $f(\"1\")")) {
            assertThatThrownBy(() -> engine.validate(expr)).as(expr)
                    .isInstanceOf(InvalidExpressionException.class);
        }
    }

    @Test
    void uelMarkersAreRefusedSoTheExpressionIsNeverInterpretedAsUel() {
        for (String expr : List.of("${1+1}", "\"a\" & \"${x}\"", "#{x}")) {
            assertThatThrownBy(() -> engine.validate(expr)).as(expr).isInstanceOf(InvalidExpressionException.class);
        }
    }

    @Test
    void controlCharactersAreRefused() {
        assertThatThrownBy(() -> engine.validate("1 + \u0000 1")).isInstanceOf(InvalidExpressionException.class);
    }

    @Test
    void anOverlyLargeSyntaxTreeIsRefused() {
        ExpressionLimits tight = ExpressionLimits.DEFAULTS.withMaxAstNodes(50);
        JsonataExpressionEngine small = new JsonataExpressionEngine(tight, CLOCK);
        assertThatThrownBy(() -> small.validate("1" + "+1".repeat(100))).isInstanceOf(InvalidExpressionException.class)
                .hasMessageContaining("too complex");
    }

    @Test
    void aTooDeepSyntaxTreeIsRefused() {
        String nested = "(".repeat(60) + "1" + ")".repeat(60);
        ExpressionLimits tight = ExpressionLimits.DEFAULTS.withMaxAstDepth(10);
        assertThatThrownBy(() -> new JsonataExpressionEngine(tight, CLOCK).validate(nested))
                .isInstanceOf(InvalidExpressionException.class);
    }

    // ---- adversarial : bornes d'exécution ----------------------------------------------------------------------

    @Test
    void unboundedRecursionIsStoppedByTheEvaluationDepthBound() {
        // Récursion non terminale : chaque appel attend le suivant, la profondeur croît (sans la borne : débordement de pile).
        assertThatThrownBy(() -> engine.evaluate("( $f := function($n){ 1 + $f($n+1) }; $f(0) )", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("nesting exceeds 400");
    }

    @Test
    void unboundedTailRecursionRunsAtConstantDepthSoTheTimeoutStopsIt() {
        // Appel terminal : la bibliothèque le déroule en boucle, la profondeur ne croît pas ; seul le délai l'arrête.
        JsonataExpressionEngine quick = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withTimeoutMillis(30), CLOCK);
        assertThatThrownBy(() -> quick.evaluate("( $f := function($n){ $f($n+1) }; $f(0) )", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void theEvaluationDepthBoundIsExactAndCountsOnlyNestedEvaluations() {
        // Un littéral est une évaluation ; un bloc qui le contient, deux.
        JsonataExpressionEngine one = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxEvalDepth(1), CLOCK);
        assertThat(one.evaluate("1", Map.of()).value()).isEqualTo(1L);
        assertThatThrownBy(() -> one.evaluate("(1)", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("nesting exceeds 1");
        assertThat(new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxEvalDepth(2), CLOCK)
                        .evaluate("(1)", Map.of()).value())
                .isEqualTo(1L);
        // Une récursion finie de 100 niveaux tient sous la borne par défaut : la profondeur redescend à chaque retour.
        assertThat(engine.evaluate("( $f := function($n){ $n = 0 ? 0 : 1 + $f($n-1) }; $f(100) )", Map.of()).value())
                .isEqualTo(100L);
    }

    @Test
    void everyIntermediateValueIsBoundedAtTheNodeThatProducesIt() {
        // Valeurs réduites à un nombre en sortie : seule la borne intermédiaire peut les refuser.
        JsonataExpressionEngine lists = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxCollectionSize(10), CLOCK);
        assertThat(lists.evaluate("$count([1..10])", Map.of()).value()).isEqualTo(10L);
        assertThatThrownBy(() -> lists.evaluate("$count([1..11])", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("an intermediate list exceeds 10 items");

        JsonataExpressionEngine objects = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxCollectionSize(2), CLOCK);
        assertThat(objects.evaluate("$count($keys({\"a\": 1, \"b\": 2}))", Map.of()).value()).isEqualTo(2L);
        assertThatThrownBy(() -> objects.evaluate("$count($keys({\"a\": 1, \"b\": 2, \"c\": 3}))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("an intermediate object exceeds 2 entries");

        JsonataExpressionEngine texts = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxResultBytes(1_000), CLOCK);
        assertThat(texts.evaluate("$length($pad(\"x\", 1000))", Map.of()).value()).isEqualTo(1_000L);
        assertThatThrownBy(() -> texts.evaluate("$length($pad(\"x\", 1001))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("an intermediate text exceeds 1000 characters");
    }

    @Test
    void aRunawayComputationIsStoppedByTheTimeout() {
        ExpressionLimits fast = ExpressionLimits.DEFAULTS.withTimeoutMillis(30);
        JsonataExpressionEngine quick = new JsonataExpressionEngine(fast, CLOCK);
        long start = System.nanoTime();
        assertThatThrownBy(() -> quick.evaluate(
                        "$count($map([1..9000], function($v){ $sum([1..9000]) }))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("timeout");
        assertThat((System.nanoTime() - start) / 1_000_000).as("stopped close to the 30 ms budget").isLessThan(1_500);
    }

    @Test
    void hugeRangesAndHugePaddingAreRefusedNotAllocated() {
        assertThatThrownBy(() -> engine.evaluate("[1..100000000]", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThat(engine.evaluate("$sum([1..1000])", Map.of()).value()).isEqualTo(500_500L);
        assertThatThrownBy(() -> engine.evaluate("$pad(\"x\", 2000000000)", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("10000");
        assertThat(engine.evaluate("$pad(\"x\", 5, \"-\")", Map.of()).value()).isEqualTo("x----");
        assertThat(engine.evaluate("$pad(\"x\", -5, \"-\")", Map.of()).value()).isEqualTo("----x");
        assertThat(engine.evaluate("$pad(\"x\", 5)", Map.of()).value()).isEqualTo("x    ");
        assertThat(engine.evaluate("$pad(\"abc\", 2)", Map.of()).value()).isEqualTo("abc");
        assertThat(engine.evaluate("$pad(\"x\", 6, \"ab\")", Map.of()).value()).isEqualTo("xababa");
    }

    @Test
    void aMemoryHungryComputationIsStoppedByTheAllocationBudget() {
        JsonataExpressionEngine small =
                new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxAllocatedBytes(1024 * 1024), CLOCK);
        assertThatThrownBy(() -> small.evaluate(
                        "$count($map([1..9000], function($v){ $string($v) & \"x\" }))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("memory");
    }

    @Test
    void padIsLinearNotQuadraticSoAWideWidthCostsNoMemoryBurst() {
        // La version de la bibliothèque alloue ~49 Mo pour une largeur de 10 000 ; la nôtre quelques Ko.
        JsonataExpressionEngine tight = new JsonataExpressionEngine(
                ExpressionLimits.DEFAULTS.withMaxAllocatedBytes(4L * 1024 * 1024), CLOCK);
        assertThat(tight.evaluate("$length($pad(\"a\", 10000)) + $length($pad(\"b\", -10000, \"xy\"))", Map.of()).value())
                .isEqualTo(20_000L);
    }

    @Test
    void anOverlargeResultIsRefused() {
        ExpressionLimits tight = ExpressionLimits.DEFAULTS.withMaxResultBytes(2_000);
        JsonataExpressionEngine small = new JsonataExpressionEngine(tight, CLOCK);
        assertThatThrownBy(() -> small.evaluate("$map([1..500], function($v){ \"xxxxxxxxxxxxxxxxxxxx\" })", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("result");
    }

    @Test
    void anOverlargeContextIsRefusedBeforeEvaluation() {
        ExpressionLimits tight = ExpressionLimits.DEFAULTS.withMaxInputBytes(1_000);
        JsonataExpressionEngine small = new JsonataExpressionEngine(tight, CLOCK);
        assertThatThrownBy(() -> small.evaluate("x", Map.of("x", "a".repeat(5_000))))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("context");
    }

    @Test
    void aContextHoldingANonJsonObjectIsRefusedSoNoJavaObjectIsEverReachable() {
        assertThatThrownBy(() -> engine.evaluate("x", Map.of("x", new Object())))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("JSON");
        assertThatThrownBy(() -> engine.evaluate("x.name", Map.of("x", Thread.currentThread())))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void aSelfReferencingContextIsRefusedNotLoopedOn() {
        Map<String, Object> loop = new java.util.HashMap<>();
        loop.put("self", loop);
        assertThatThrownBy(() -> engine.evaluate("self", Map.of("x", loop)))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    // ---- revue adversariale LC2 (constats reproduits par sonde) -------------------------------------------------------

    @Test
    void criticalNoExpressionCanMaskTheEngineGuardsByBindingAnInternalName() {
        // Constat 1 : `$__evaluate_entry := nope` masquait les callbacks (délai, mémoire, profondeur) du bloc courant.
        for (String expr : List.of(
                "($__evaluate_entry := nope; $__evaluate_exit := nope; $f := function($n){ $n >= 20000000 ? $n : $f($n+1) }; $f(0))",
                "($__evaluate_entry := nope; 1)",
                "($__evaluate_exit := nope; 1)",
                "function($__evaluate_entry){1}(2)",
                "x@$__evaluate_entry.y",
                "x#$__evaluate_exit.y",
                "($__anything := 1; $__anything)")) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> engine.validate(expr)).as(expr)
                    .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("__");
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
        }
    }

    @Test
    void theMemoryBypassExpressionIsRefusedBeforeItCanExhaustTheHeap() {
        assertThatThrownBy(() -> engine.evaluate(
                        "($__evaluate_entry := nope; $__evaluate_exit := nope;"
                                + " $length($reduce([1..40], function($a,$b){$a & $a}, \"xxxxxxxx\")))", Map.of()))
                .isInstanceOf(InvalidExpressionException.class);
    }

    @Test
    void criticalReplaceWithALimitIsLiteralAndNeverARegex() {
        // Constat 2 : avec un 4ᵉ argument, dashjoin appelait String.replaceFirst (motif interprété en regex, ReDoS).
        assertThat(engine.evaluate("$replace(\"abc\", \"(b)\", \"[$1]\", 1)", Map.of()).value()).isEqualTo("abc");
        assertThat(engine.evaluate("$replace(\"a(b)c\", \"(b)\", \"[$1]\", 1)", Map.of()).value()).isEqualTo("a[$1]c");
        assertThat(engine.evaluate("$replace(\"aaaa\", \"a\", \"bb\", 2)", Map.of()).value()).isEqualTo("bbbbaa");
        assertThat(engine.evaluate("$replace(\"a-b-c\", \"-\", \"+\")", Map.of()).value()).isEqualTo("a+b+c");
        long start = System.nanoTime();
        assertThat(engine.evaluate(
                        "$replace(\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab\", \"^(.*a){12}$\", \"z\", 1)", Map.of()).value())
                .isEqualTo("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaab");
        assertThat(engine.evaluate("$replace(\"a\", \"b\", \"c\", 10000000)", Map.of()).value()).isEqualTo("a");
        assertThat((System.nanoTime() - start) / 1_000_000).as("no backtracking, no native loop").isLessThan(1_000);
    }

    @Test
    void replaceRefusesEmptyPatternsNegativeLimitsAndOversizedResults() {
        assertThatThrownBy(() -> engine.evaluate("$replace(\"abc\", \"\", \"x\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThatThrownBy(() -> engine.evaluate("$replace(\"abc\", \"a\", \"x\", -1)", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThatThrownBy(() -> engine.evaluate(
                        "$replace($pad(\"a\", 10000, \"a\"), \"a\", $pad(\"b\", 10000))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("exceeds");
        assertThatThrownBy(() -> engine.evaluate("$replace(\"abc\", \"a\", function($m){ \"x\" })", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void highSingleHugeCollectionsAndStringsAreRefusedAtTheNodeThatProducesThem() {
        // Constat 3 : `[1..9999999]` (plafond réel 1e7) puis `$string`/`$distinct`/… en un seul appel natif → OOM.
        for (String expr : List.of(
                "[1..9999999]",
                "$string([1..9999999])",
                "$sum([1..9999999])",
                "$length($string([[1..9999999],[1..9999999],[1..9999999],[1..9999999],[1..9999999],[1..9999999]]))",
                "$count($distinct([[1..9999999],[1..9999999],[1..9999999]]))")) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> engine.evaluate(expr, Map.of())).as(expr)
                    .isInstanceOf(ExpressionEvaluationException.class).hasMessageContaining("exceeds");
            assertThat((System.nanoTime() - start) / 1_000_000).as(expr).isLessThan(2_000);
        }
        assertThatThrownBy(() -> engine.evaluate(
                        "$length($reduce([1..40], function($a,$b){$a & $a}, \"xxxxxxxx\"))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class).hasMessageContaining("exceeds");
        assertThat(engine.evaluate("$count([1..9999])", Map.of()).value()).isEqualTo(9_999L);
    }

    @Test
    void mediumErrorMessagesNeverLeakContextValuesOrInternals() {
        Map<String, Object> ctx = Map.of("secret", "TOPSECRET-VALUE", "x", 1);
        for (String expr : List.of("$split(\"abc\", 1)", "x + \"a\"", "$sum(\"a\")", "$substring(1, \"b\")",
                "$number(\"zzz\")", "$count(1,2,3)")) {
            assertThatThrownBy(() -> engine.evaluate(expr, ctx)).as(expr)
                    .isInstanceOf(ExpressionEvaluationException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("TOPSECRET").doesNotContain("java.")
                            .doesNotContain("com.dashjoin").doesNotContain("class ").doesNotContain("{x=")
                            .doesNotContain("secret"));
        }
        // Utile à l'auteur : le message statique de la bibliothèque est conservé, sans les valeurs.
        assertThatThrownBy(() -> engine.evaluate("1 + \"a\"", Map.of()))
                .hasMessageContaining("must evaluate to a number");
        // `$error(...)` est un texte choisi par l'auteur : il est restitué tel quel.
        assertThatThrownBy(() -> engine.evaluate("$error(\"montant manquant\")", Map.of()))
                .hasMessageContaining("montant manquant");
    }

    @Test
    void evaluationFailuresCarryNoCauseThatCouldLogRawValues() {
        assertThatThrownBy(() -> engine.evaluate("$split(\"abc\", 1)", Map.of("secret", "TOPSECRET-VALUE")))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasNoCause();
    }

    @Test
    void lowEveryBuiltinOutsideTheAllowlistIsRefusedAndEveryAllowedOneIsKnownToTheLibrary() {
        java.util.Set<String> builtins = AstInspector.builtinFunctionNames();
        // {@code $ascii} est ajoutée par le moteur : elle n'est pas une fonction de la bibliothèque.
        assertThat(JsonataExpressionEngine.ALLOWED_FUNCTIONS).contains("ascii");
        assertThat(builtins).containsAll(JsonataExpressionEngine.ALLOWED_FUNCTIONS.stream()
                .filter(name -> !"ascii".equals(name)).toList());
        engine.validate("$ascii");
        for (String name : builtins) {
            if (JsonataExpressionEngine.ALLOWED_FUNCTIONS.contains(name)) {
                engine.validate("$" + name); // référence seule : syntaxe valide
            } else {
                assertThatThrownBy(() -> engine.validate("$" + name)).as("$" + name)
                        .isInstanceOf(InvalidExpressionException.class);
            }
        }
        assertThat(builtins).contains("shuffle", "random", "eval", "now", "millis", "match", "formatInteger", "parseInteger");
        assertThatThrownBy(() -> engine.validate("$shuffle([1,2,3])")).isInstanceOf(InvalidExpressionException.class);
    }

    @Test
    void dateFunctionsTakeNoPictureSoNoUserSuppliedFormatReachesTheDateEngine() {
        assertThat(engine.evaluate("$toMillis(\"2026-09-30T10:00:00Z\")", Map.of()).value()).isInstanceOf(Long.class);
        assertThat(engine.evaluate("$fromMillis(0)", Map.of()).value()).isEqualTo("1970-01-01T00:00:00.000Z");
        assertThatThrownBy(() -> engine.evaluate("$toMillis(\"2026\", \"[Y0001]\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThatThrownBy(() -> engine.evaluate("$fromMillis(0, \"[Y0001]\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThatThrownBy(() -> engine.evaluate("$toMillis(\"pas une date\")", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void anErrorThrownByTheRuntimeIsNotSwallowedIntoABadRequest() {
        // Une StackOverflowError profonde (parseur ou évaluation) devient un échec neutre ; jamais un message de classe.
        String deep = "(".repeat(900) + "1" + ")".repeat(900);
        try {
            engine.evaluate(deep, Map.of());
        } catch (InvalidExpressionException | ExpressionEvaluationException e) {
            assertThat(e.getMessage()).doesNotContain("java.lang");
        }
    }

    // ---- seconde passe adversariale : amplification par partage de sous-structures ---------------------------------

    private static final String DAG = "$t := function($n){ $n=0 ? \"x\" : ($c := $t($n-1); {\"a\":$c,\"b\":$c}) };";

    /** Enchaîne des instructions dans un bloc : JSONata exige des parenthèses autour de `;`. */
    private static String block(String statements) {
        return "(" + DAG + " " + statements + ")";
    }

    private void assertRefusedFast(String expr) {
        long start = System.nanoTime();
        assertThatThrownBy(() -> engine.evaluate(expr, Map.of())).as(expr)
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThat((System.nanoTime() - start) / 1_000_000).as(expr + " (ms)").isLessThan(3_000);
    }

    @Test
    void criticalPathFlatteningOverASharedListIsStoppedByTheWorkBudgetBeforeTheNativeStep() {
        // C1 : `[1..9999].$l` aplatit 9999 × 9999 références dans UNE étape native : OutOfMemoryError (2,7 s à 256 Mo).
        assertRefusedFast("($l := [1..9999]; [1..9999].$l)");
        assertRefusedFast("($x := $join([1..6500].(\"xxxxxxxxxx\")); $length($join([1..9999].$x)))");
        assertRefusedFast("($x := $join([1..6500].(\"xxxxxxxxxx\")); $length($string([1..9999].$x)))");
        assertRefusedFast("($l := [1..9999]; $count($distinct([1..9999].$l)))");
        assertRefusedFast("($o := $merge([1..9999].{$string($): 1}); $count($keys([1..9999].$o)))");
        assertRefusedFast("($o := $merge([1..9999].{$string($): 1}); $spread([1..9999].$o))");
    }

    @Test
    void criticalSharedSubtreesHaveABoundedExpandedWeightSoEqualityAndHashingStayLinear() {
        // C2 : `{"a":$c,"b":$c}` imbriqué 28 fois = 2^28 chemins pour 28 objets ; `=` et `$distinct` parcourent tout.
        assertRefusedFast(block("$a := $t(28); $b := $t(28); $a = $b"));
        assertRefusedFast(block("$t(24)"));
        assertRefusedFast(block("$string($t(24))"));
        assertRefusedFast(block("$distinct([$t(22), 1])"));
        // Un arbre partagé de poids raisonnable reste permis (2^10 = 1024 feuilles).
        assertThat(engine.evaluate(block("$a := $t(10); $b := $t(10); $a = $b"), Map.of()).value()).isEqualTo(true);
    }

    @Test
    void theExpandedWeightBoundIsTheOneThatRefusesASharedTree() {
        // Poids déplié de $t(n) : 3 + 2 × poids de $t(n-1), soit 61 pour n = 4 et 125 pour n = 5 ; aucune autre borne
        // n'est approchée à cette taille.
        JsonataExpressionEngine light = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxValueWeight(100), CLOCK);
        assertThat(light.evaluate(block("$t(4)"), Map.of()).defined()).isTrue();
        assertThatThrownBy(() -> light.evaluate(block("$t(5)"), Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("too large once shared parts are counted each time (max 100)");
        // Une liste aussi : l'objet {"k": texte de 1 500} pèse 96, la liste qui le cite une fois 97, deux fois 193.
        JsonataExpressionEngine medium = new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxValueWeight(150), CLOCK);
        String object = "$m := {\"k\": $pad(\"x\", 1500)};";
        assertThat(medium.evaluate("(" + object + " $count([$m]))", Map.of()).value()).isEqualTo(1L);
        assertThatThrownBy(() -> medium.evaluate("(" + object + " $count([$m, $m]))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("(max 150)");
    }

    @Test
    void legitimateBusinessExpressionsStillFitComfortablyInTheBudgets() {
        List<Object> lines = new ArrayList<>();
        for (int i = 0; i < 3_000; i++) {
            lines.add(Map.of("sku", "SKU-" + i, "qty", i % 7 + 1, "price", 10 + i % 13, "tags", List.of("a", "b")));
        }
        Map<String, Object> ctx = Map.of("connectorResult", Map.of("lignes", lines));
        long start = System.nanoTime();
        assertThat(engine.evaluate("$sum($map(connectorResult.lignes, function($l){ $l.qty * $l.price }))", ctx).value())
                .isInstanceOf(Long.class);
        assertThat(engine.evaluate("$count(connectorResult.lignes[qty > 3])", ctx).value()).isInstanceOf(Long.class);
        assertThat(engine.evaluate("$count($distinct(connectorResult.lignes.qty))", ctx).value()).isEqualTo(7L);
        assertThat(engine.evaluate("$join($sort(connectorResult.lignes[price > 20].sku)[[0..2]], \",\")", ctx).value())
                .isInstanceOf(String.class);
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(2_000);
    }

    @Test
    void lowAnIndexBindingBehindAPredicateCannotSmuggleAReservedName() {
        // B1 : `#$nom` après un prédicat n'était pas vu de l'inspecteur.
        for (String expr : List.of("items[a>0]#$__q.a", "items[a>0]#$__evaluate_entry.a", "items[a>0]#$__evaluate_exit.a")) {
            assertThatThrownBy(() -> engine.validate(expr)).as(expr)
                    .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("__");
        }
        engine.validate("items[a>0]#$i.a"); // la forme légitime reste permise
    }
}
