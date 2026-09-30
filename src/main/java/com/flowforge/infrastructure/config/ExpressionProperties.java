package com.flowforge.infrastructure.config;

import com.flowforge.domain.expression.model.ExpressionLimits;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TODO §75 (LC2) — bornes du moteur d'expressions métier ({@code app.expression.*}). Toute valeur non positive (ou absente)
 * retombe sur la valeur par défaut de {@link ExpressionLimits#DEFAULTS}. Les mêmes bornes s'appliquent à la validation,
 * à l'évaluation à blanc (API, MCP) et à l'exécution : ne les relever qu'en connaissance de cause.
 */
@ConfigurationProperties(prefix = "app.expression")
public record ExpressionProperties(
        int maxExpressionChars,
        int maxAstNodes,
        int maxAstDepth,
        long timeoutMillis,
        int maxEvalDepth,
        int maxInputBytes,
        int maxResultBytes,
        int maxStringChars,
        long maxAllocatedBytes,
        int maxCollectionSize) {

    public ExpressionLimits toLimits() {
        ExpressionLimits d = ExpressionLimits.DEFAULTS;
        return new ExpressionLimits(
                positive(maxExpressionChars, d.maxExpressionChars()),
                positive(maxAstNodes, d.maxAstNodes()),
                positive(maxAstDepth, d.maxAstDepth()),
                positive(timeoutMillis, d.timeoutMillis()),
                positive(maxEvalDepth, d.maxEvalDepth()),
                positive(maxInputBytes, d.maxInputBytes()),
                positive(maxResultBytes, d.maxResultBytes()),
                positive(maxStringChars, d.maxStringChars()),
                positive(maxAllocatedBytes, d.maxAllocatedBytes()),
                positive(maxCollectionSize, d.maxCollectionSize()));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
