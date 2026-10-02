package com.flowforge.infrastructure.expression;

import com.dashjoin.jsonata.Jsonata;
import com.flowforge.domain.expression.model.ExpressionEvaluationException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Copie défensive d'un contexte vers des types JSON purs, avec estimation de taille. Seuls {@code String}, nombres,
 * {@code Boolean}, {@code null}, {@code Map<String,?>} et {@code List} passent : aucun objet Java arbitraire (thread,
 * classe, date…) n'est jamais atteignable depuis une expression. Les cycles sont refusés.
 */
final class JsonValues {

    private static final int MAX_DEPTH = 32;

    private final int maxBytes;
    private final boolean forInput;
    private int bytes;
    private final Set<Object> path = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private JsonValues(int maxBytes, boolean forInput) {
        this.maxBytes = maxBytes;
        this.forInput = forInput;
    }

    /** Copie le contexte d'entrée. */
    static Map<String, Object> copyContext(Map<String, Object> context, int maxBytes) {
        JsonValues copier = new JsonValues(maxBytes, true);
        @SuppressWarnings("unchecked")
        Map<String, Object> copy = (Map<String, Object>) copier.copy(context, 0, "context");
        return copy;
    }

    /** Normalise un résultat d'évaluation (types JSON purs) et refuse un résultat trop gros. */
    static Object copyResult(Object value, int maxBytes) {
        JsonValues copier = new JsonValues(maxBytes, false);
        return copier.copy(value, 0, "result");
    }

    private Object copy(Object value, int depth, String what) {
        if (depth > MAX_DEPTH) {
            throw new ExpressionEvaluationException(what + " is nested too deeply (max " + MAX_DEPTH + ")");
        }
        if (value == null || value == Jsonata.NULL_VALUE) {
            add(4, what);
            return null;
        }
        if (value instanceof CharSequence s) {
            add(s.length() + 2L, what);
            return s.toString();
        }
        if (value instanceof Boolean b) {
            add(5, what);
            return b;
        }
        if (value instanceof Number n) {
            add(8, what);
            return normalizeNumber(n, what);
        }
        if (value instanceof Map<?, ?> map) {
            enter(value, what);
            try {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    if (!(e.getKey() instanceof String key)) {
                        throw new ExpressionEvaluationException(what + " must hold only JSON values (object keys must be strings)");
                    }
                    add(key.length() + 3L, what);
                    out.put(key, copy(e.getValue(), depth + 1, what));
                }
                return out;
            } finally {
                path.remove(value);
            }
        }
        if (value instanceof List<?> list) {
            enter(value, what);
            try {
                List<Object> out = new ArrayList<>(list.size());
                for (Object item : list) {
                    Object copied = copy(item, depth + 1, what);
                    out.add(copied == null && forInput ? Jsonata.NULL_VALUE : copied);
                }
                return out;
            } finally {
                path.remove(value);
            }
        }
        throw new ExpressionEvaluationException(
                what + " must hold only JSON values (found " + value.getClass().getSimpleName() + ")");
    }

    private void enter(Object container, String what) {
        if (!path.add(container)) {
            throw new ExpressionEvaluationException(what + " must not contain a cycle");
        }
    }

    private void add(long delta, String what) {
        bytes += (int) Math.min(delta, Integer.MAX_VALUE);
        if (bytes < 0 || bytes > maxBytes) {
            throw new ExpressionEvaluationException(what + " exceeds " + maxBytes + " bytes");
        }
    }

    private static Object normalizeNumber(Number n, String what) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return n.longValue();
        }
        double d = n.doubleValue();
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new ExpressionEvaluationException(what + " holds a non-finite number");
        }
        if (d == Math.rint(d) && Math.abs(d) < 9_007_199_254_740_992d) {
            return (long) d;
        }
        return d;
    }
}
