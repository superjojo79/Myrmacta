package com.flowforge.infrastructure.expression;

import com.dashjoin.jsonata.AstInspector;
import com.dashjoin.jsonata.JException;
import com.dashjoin.jsonata.Jsonata;
import com.flowforge.domain.expression.model.ExpressionEvaluationException;
import com.flowforge.domain.expression.model.ExpressionLimits;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.model.InvalidExpressionException;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §75 (LC2) — moteur d'expressions métier JSONata, <b>borné</b> et sans accès au serveur.
 *
 * <p>Défenses, toutes vérifiées par {@code JsonataExpressionEngineTest} :
 * <ul>
 *   <li><b>statique</b> (avant toute évaluation, par inspection de l'arbre) : longueur, taille et profondeur de l'arbre,
 *       littéraux regex refusés (le retour arrière d'une regex Java n'est pas interruptible par le délai), fonctions
 *       interdites ({@code $eval}, non déterministes {@code $random}/{@code $now}/{@code $millis}), marqueurs UEL
 *       ({@code ${}, {@code #{}) et caractères de contrôle refusés ;</li>
 *   <li><b>dynamique</b> : délai et profondeur d'évaluations imbriquées, budget mémoire alloué par le thread, plafond de
 *       chaîne pour {@code $pad}, plafond d'intervalle natif (1e6) ;</li>
 *   <li><b>données</b> : contexte d'entrée copié vers des types JSON purs (aucun objet Java atteignable, cycles refusés),
 *       taille bornée en entrée et en sortie ; l'horloge {@code now} est injectée, jamais fournie par l'appelant.</li>
 * </ul>
 * Jamais évalué en JUEL ; jamais concaténé dans une expression UEL.
 */
public final class JsonataExpressionEngine implements ExpressionEnginePort {

    private static final Set<String> FORBIDDEN_FUNCTIONS = Set.of("eval", "random", "now", "millis");

    private final ExpressionLimits limits;
    private final Clock clock;
    private final com.sun.management.ThreadMXBean allocations = allocationBean();

    public JsonataExpressionEngine(ExpressionLimits limits, Clock clock) {
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void validate(String expression) {
        inspect(expression);
    }

    @Override
    public ExpressionResult evaluate(String expression, Map<String, Object> context) {
        inspect(expression);
        Map<String, Object> input = JsonValues.copyContext(context == null ? Map.of() : context, limits.maxInputBytes());
        Map<String, Object> withClock = new LinkedHashMap<>(input);
        withClock.put("now", clock.instant().truncatedTo(ChronoUnit.SECONDS).toString());

        Object raw = run(expression, withClock);
        if (raw == null) {
            return ExpressionResult.undefined();
        }
        if (raw != Jsonata.NULL_VALUE && !isJson(raw)) {
            throw new ExpressionEvaluationException("expression must produce a JSON value, not a function");
        }
        return ExpressionResult.of(JsonValues.copyResult(raw, limits.maxResultBytes()));
    }

    // ---- statique ----------------------------------------------------------------------------------------------

    private void inspect(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new InvalidExpressionException("expression must not be blank");
        }
        if (expression.length() > limits.maxExpressionChars()) {
            throw new InvalidExpressionException(
                    "expression exceeds " + limits.maxExpressionChars() + " characters");
        }
        if (expression.contains("${") || expression.contains("#{")) {
            throw new InvalidExpressionException("expression must not contain ${ or #{ (not a UEL expression)");
        }
        if (expression.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new InvalidExpressionException("expression contains control characters");
        }
        AstInspector.Report report;
        try {
            report = AstInspector.inspect(expression);
        } catch (JException e) {
            throw new InvalidExpressionException(syntaxMessage(e), Math.max(e.getLocation(), 0));
        } catch (StackOverflowError e) {
            throw new InvalidExpressionException("expression is too complex (nested too deeply)");
        }
        if (report.usesRegex()) {
            throw new InvalidExpressionException(
                    "regular expression literals are not allowed (use a plain string pattern)");
        }
        for (String name : report.variables()) {
            if (FORBIDDEN_FUNCTIONS.contains(name)) {
                throw new InvalidExpressionException("function $" + name + " is not allowed"
                        + (Set.of("now", "millis").contains(name) ? " (use the `now` context value)" : ""));
            }
        }
        if (report.nodes() > limits.maxAstNodes() || report.depth() > limits.maxAstDepth()) {
            throw new InvalidExpressionException("expression is too complex (max " + limits.maxAstNodes()
                    + " nodes, depth " + limits.maxAstDepth() + ")");
        }
    }

    private static String syntaxMessage(JException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "invalid expression (" + e.getError() + ")" : message;
    }

    // ---- dynamique ---------------------------------------------------------------------------------------------

    private Object run(String expression, Map<String, Object> input) {
        Jsonata jsonata = Jsonata.jsonata(expression);
        // Un null explicite doit rester distinct d'un résultat absent (NULL_VALUE vs null Java).
        jsonata.setOutputConvertNulls(false);
        Jsonata.Frame bindings = jsonata.createFrame();
        bindings.bind("pad", boundedPad());
        bindGuards(bindings);
        try {
            return jsonata.evaluate(input, bindings);
        } catch (ExpressionEvaluationException e) {
            throw e;
        } catch (JException e) {
            throw new ExpressionEvaluationException(runtimeMessage(e), e);
        } catch (StackOverflowError e) {
            throw new ExpressionEvaluationException("expression is nested too deeply to evaluate");
        } catch (RuntimeException e) {
            throw new ExpressionEvaluationException("expression could not be evaluated: " + e.getMessage(), e);
        }
    }

    private void bindGuards(Jsonata.Frame bindings) {
        long deadline = System.nanoTime() + limits.timeoutMillis() * 1_000_000L;
        long allocationStart = currentAllocated();
        int[] depth = {0};
        Runnable check = () -> {
            if (System.nanoTime() - deadline > 0) {
                throw new ExpressionEvaluationException(
                        "expression evaluation timeout (" + limits.timeoutMillis() + " ms)");
            }
            if (allocations != null && currentAllocated() - allocationStart > limits.maxAllocatedBytes()) {
                throw new ExpressionEvaluationException(
                        "expression exceeded its memory budget (" + limits.maxAllocatedBytes() + " bytes)");
            }
        };
        bindings.setEvaluateEntryCallback((expr, in, env) -> {
            if (++depth[0] > limits.maxEvalDepth()) {
                throw new ExpressionEvaluationException(
                        "expression nesting exceeds " + limits.maxEvalDepth() + " (non-terminating recursion?)");
            }
            check.run();
        });
        bindings.setEvaluateExitCallback((expr, in, env, result) -> {
            depth[0]--;
            check.run();
        });
    }

    private Jsonata.JFunction boundedPad() {
        return Jsonata.function(
                "pad",
                (Jsonata.Fn3<String, Number, String, String>) (str, width, padChar) -> {
                    if (width != null && Math.abs(width.doubleValue()) > limits.maxStringChars()) {
                        throw new ExpressionEvaluationException(
                                "$pad width exceeds " + limits.maxStringChars() + " characters");
                    }
                    return com.dashjoin.jsonata.Functions.pad(str, width, padChar);
                },
                "<s-ns?:s>");
    }

    private static String runtimeMessage(JException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "expression error (" + e.getError() + ")" : message;
    }

    private static boolean isJson(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Map
                || value instanceof java.util.List;
    }

    private long currentAllocated() {
        return allocations == null ? 0L : allocations.getCurrentThreadAllocatedBytes();
    }

    private static com.sun.management.ThreadMXBean allocationBean() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported()) {
            bean.setThreadAllocatedMemoryEnabled(true);
            return bean;
        }
        return null;
    }

    /** Instant courant tel que vu par le moteur (exposé aux tests). */
    Instant now() {
        return clock.instant();
    }
}
