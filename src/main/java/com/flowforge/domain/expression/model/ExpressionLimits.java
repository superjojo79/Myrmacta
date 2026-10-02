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
 * @param maxResultBytes taille maximale (estimée, JSON) du résultat ; aussi la longueur maximale de toute chaîne
 *     intermédiaire (contrôlée à chaque nœud)
 * @param maxStringChars largeur maximale de {@code $pad}
 * @param maxAllocatedBytes mémoire maximale allouée par le thread pendant une évaluation
 * @param maxCollectionSize nombre maximal d'éléments de toute liste ou objet intermédiaire (contrôlé à chaque nœud)
 * @param maxValueWeight poids <b>déplié</b> maximal d'une valeur intermédiaire : chaque occurrence d'un sous-arbre
 *     <em>partagé</em> est comptée. Sans cela, {@code {"a":$c,"b":$c}} imbriqué n fois tient en n objets mais compte 2^n
 *     chemins, que l'égalité ({@code =}) ou {@code $distinct} parcourent sans allouer ni rendre la main au délai
 * @param maxWork budget de travail <b>cumulé</b> d'une évaluation (somme, à chaque nœud, de la taille de la valeur
 *     produite). Un aplatissement de chemin ({@code [1..9999].$l}) ou un appel natif sur des références partagées évalue
 *     d'abord chaque référence : le budget s'épuise avant l'étape native
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
        long maxAllocatedBytes,
        int maxCollectionSize,
        long maxValueWeight,
        long maxWork) {

    public static final ExpressionLimits DEFAULTS = new ExpressionLimits(
            2_000, 500, 40, 250L, 400, 1024 * 1024, 64 * 1024, 10_000, 64L * 1024 * 1024, 10_000, 1_000_000L, 3_000_000L);

    public ExpressionLimits {
        if (maxExpressionChars < 1 || maxAstNodes < 1 || maxAstDepth < 1 || timeoutMillis < 1 || maxEvalDepth < 1
                || maxInputBytes < 1 || maxResultBytes < 1 || maxStringChars < 1 || maxAllocatedBytes < 1
                || maxCollectionSize < 1 || maxValueWeight < 1 || maxWork < 1) {
            throw new IllegalArgumentException("every expression limit must be positive");
        }
    }

    public ExpressionLimits withMaxAstNodes(int value) {
        return toBuilder().maxAstNodes(value).build();
    }

    public ExpressionLimits withMaxAstDepth(int value) {
        return toBuilder().maxAstDepth(value).build();
    }

    public ExpressionLimits withTimeoutMillis(long value) {
        return toBuilder().timeoutMillis(value).build();
    }

    public ExpressionLimits withMaxInputBytes(int value) {
        return toBuilder().maxInputBytes(value).build();
    }

    public ExpressionLimits withMaxResultBytes(int value) {
        return toBuilder().maxResultBytes(value).build();
    }

    public ExpressionLimits withMaxAllocatedBytes(long value) {
        return toBuilder().maxAllocatedBytes(value).build();
    }

    public ExpressionLimits withMaxCollectionSize(int value) {
        return toBuilder().maxCollectionSize(value).build();
    }

    public ExpressionLimits withMaxValueWeight(long value) {
        return toBuilder().maxValueWeight(value).build();
    }

    public ExpressionLimits withMaxWork(long value) {
        return toBuilder().maxWork(value).build();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Copie modifiable ; évite douze paramètres positionnels à chaque variante. */
    public static final class Builder {
        private int maxExpressionChars;
        private int maxAstNodes;
        private int maxAstDepth;
        private long timeoutMillis;
        private int maxEvalDepth;
        private int maxInputBytes;
        private int maxResultBytes;
        private int maxStringChars;
        private long maxAllocatedBytes;
        private int maxCollectionSize;
        private long maxValueWeight;
        private long maxWork;

        private Builder(ExpressionLimits from) {
            this.maxExpressionChars = from.maxExpressionChars;
            this.maxAstNodes = from.maxAstNodes;
            this.maxAstDepth = from.maxAstDepth;
            this.timeoutMillis = from.timeoutMillis;
            this.maxEvalDepth = from.maxEvalDepth;
            this.maxInputBytes = from.maxInputBytes;
            this.maxResultBytes = from.maxResultBytes;
            this.maxStringChars = from.maxStringChars;
            this.maxAllocatedBytes = from.maxAllocatedBytes;
            this.maxCollectionSize = from.maxCollectionSize;
            this.maxValueWeight = from.maxValueWeight;
            this.maxWork = from.maxWork;
        }

        public Builder maxAstNodes(int v) {
            this.maxAstNodes = v;
            return this;
        }

        public Builder maxAstDepth(int v) {
            this.maxAstDepth = v;
            return this;
        }

        public Builder timeoutMillis(long v) {
            this.timeoutMillis = v;
            return this;
        }

        public Builder maxInputBytes(int v) {
            this.maxInputBytes = v;
            return this;
        }

        public Builder maxResultBytes(int v) {
            this.maxResultBytes = v;
            return this;
        }

        public Builder maxAllocatedBytes(long v) {
            this.maxAllocatedBytes = v;
            return this;
        }

        public Builder maxCollectionSize(int v) {
            this.maxCollectionSize = v;
            return this;
        }

        public Builder maxValueWeight(long v) {
            this.maxValueWeight = v;
            return this;
        }

        public Builder maxWork(long v) {
            this.maxWork = v;
            return this;
        }

        public ExpressionLimits build() {
            return new ExpressionLimits(maxExpressionChars, maxAstNodes, maxAstDepth, timeoutMillis, maxEvalDepth,
                    maxInputBytes, maxResultBytes, maxStringChars, maxAllocatedBytes, maxCollectionSize, maxValueWeight,
                    maxWork);
        }
    }
}
