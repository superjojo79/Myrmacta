package com.flowforge.domain.expression.model;

/**
 * Bornes d'évaluation d'une expression métier (TODO §75, D-EXPR-LIMITS). Identiques en validation, en évaluation à blanc
 * (API, MCP) et en exécution réelle (delegate BPMN) : une expression acceptée à la conception ne change pas de budget à
 * l'exécution.
 *
 * @param maxExpressionChars longueur maximale du texte de l'expression
 * @param maxAstNodes nombre maximal de nœuds de l'arbre syntaxique
 * @param maxAstDepth profondeur syntaxique maximale
 * @param timeoutMillis durée maximale d'une évaluation
 * @param maxEvalDepth profondeur maximale d'évaluations imbriquées (borne la récursion de fonctions)
 * @param maxInputBytes taille maximale (estimée, JSON) du contexte d'entrée
 * @param maxResultBytes taille maximale (estimée, JSON) du résultat
 * @param maxStringChars longueur maximale d'une chaîne produite par remplissage ({@code $pad})
 * @param maxAllocatedBytes mémoire maximale allouée par le thread pendant une évaluation
 */
public record ExpressionLimits(
        int maxExpressionChars,
        int maxAstNodes,
        int maxAstDepth,
        long timeoutMillis,
        int maxEvalDepth,
        int maxInputBytes,
        int maxResultBytes,
        int maxStringChars,
        long maxAllocatedBytes) {

    public static final ExpressionLimits DEFAULTS =
            new ExpressionLimits(2_000, 500, 40, 250L, 400, 256 * 1024, 64 * 1024, 10_000, 64L * 1024 * 1024);

    public ExpressionLimits {
        if (maxExpressionChars < 1 || maxAstNodes < 1 || maxAstDepth < 1 || timeoutMillis < 1 || maxEvalDepth < 1
                || maxInputBytes < 1 || maxResultBytes < 1 || maxStringChars < 1 || maxAllocatedBytes < 1) {
            throw new IllegalArgumentException("every expression limit must be positive");
        }
    }

    public ExpressionLimits withMaxAstNodes(int value) {
        return new ExpressionLimits(maxExpressionChars, value, maxAstDepth, timeoutMillis, maxEvalDepth, maxInputBytes,
                maxResultBytes, maxStringChars, maxAllocatedBytes);
    }

    public ExpressionLimits withMaxAstDepth(int value) {
        return new ExpressionLimits(maxExpressionChars, maxAstNodes, value, timeoutMillis, maxEvalDepth, maxInputBytes,
                maxResultBytes, maxStringChars, maxAllocatedBytes);
    }

    public ExpressionLimits withTimeoutMillis(long value) {
        return new ExpressionLimits(maxExpressionChars, maxAstNodes, maxAstDepth, value, maxEvalDepth, maxInputBytes,
                maxResultBytes, maxStringChars, maxAllocatedBytes);
    }

    public ExpressionLimits withMaxInputBytes(int value) {
        return new ExpressionLimits(maxExpressionChars, maxAstNodes, maxAstDepth, timeoutMillis, maxEvalDepth, value,
                maxResultBytes, maxStringChars, maxAllocatedBytes);
    }

    public ExpressionLimits withMaxResultBytes(int value) {
        return new ExpressionLimits(maxExpressionChars, maxAstNodes, maxAstDepth, timeoutMillis, maxEvalDepth,
                maxInputBytes, value, maxStringChars, maxAllocatedBytes);
    }

    public ExpressionLimits withMaxAllocatedBytes(long value) {
        return new ExpressionLimits(maxExpressionChars, maxAstNodes, maxAstDepth, timeoutMillis, maxEvalDepth,
                maxInputBytes, maxResultBytes, maxStringChars, value);
    }
}
