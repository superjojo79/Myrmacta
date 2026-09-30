package com.flowforge.infrastructure.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.expression.model.ExpressionEvaluationException;
import com.flowforge.domain.expression.model.ExpressionLimits;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.model.InvalidExpressionException;
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
    void unboundedRecursionIsStoppedByTheCallDepthBound() {
        assertThatThrownBy(() -> engine.evaluate("( $f := function($n){ $f($n+1) }; $f(0) )", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
    }

    @Test
    void aRunawayComputationIsStoppedByTheTimeout() {
        ExpressionLimits fast = ExpressionLimits.DEFAULTS.withTimeoutMillis(30);
        JsonataExpressionEngine quick = new JsonataExpressionEngine(fast, CLOCK);
        long start = System.nanoTime();
        assertThatThrownBy(() -> quick.evaluate(
                        "$count($map([1..900000], function($v){ $sum([1..200]) }))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("timeout");
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5_000);
    }

    @Test
    void hugeRangesAndHugePaddingAreRefusedNotAllocated() {
        assertThatThrownBy(() -> engine.evaluate("[1..100000000]", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class);
        assertThatThrownBy(() -> engine.evaluate("$pad(\"x\", 2000000000)", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("10000");
        assertThat(engine.evaluate("$pad(\"x\", 5, \"-\")", Map.of()).value()).isEqualTo("x----");
    }

    @Test
    void aMemoryHungryComputationIsStoppedByTheAllocationBudget() {
        JsonataExpressionEngine small =
                new JsonataExpressionEngine(ExpressionLimits.DEFAULTS.withMaxAllocatedBytes(1024 * 1024), CLOCK);
        assertThatThrownBy(() -> small.evaluate(
                        "$count($map([1..50000], function($v){ $string($v) & \"x\" }))", Map.of()))
                .isInstanceOf(ExpressionEvaluationException.class)
                .hasMessageContaining("memory");
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
}
