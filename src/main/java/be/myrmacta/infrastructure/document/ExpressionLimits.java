package be.myrmacta.infrastructure.document;

import org.thymeleaf.exceptions.TemplateProcessingException;

/**
 * Bornes de coût de l'évaluateur D-40 pour les gabarits de notification (registre §123). Les templates de document n'en
 * ont pas ({@code null}) : leur comportement ne change pas.
 *
 * @param utilityMaxSize taille maximale (éléments ou caractères) de {@code #numbers.sequence} et {@code #strings.repeat} ;
 *     plafond des chiffres demandés à {@code #numbers.format*} ; {@code #strings.randomAlphanumeric} est refusé
 * @param stringMaxChars longueur maximale de toute chaîne produite par une évaluation ou passée à un utilitaire, et taille
 *     maximale de toute collection produite par un utilitaire
 * @param budget budget par rendu : chaque évaluation coûte 1, plus la longueur de l'expression et la taille des opérandes
 *     et du résultat (une chaîne pèse sa longueur divisée par 64)
 * @param timeoutMillis durée maximale d'un rendu, contrôlée à chaque évaluation
 */
public record ExpressionLimits(int utilityMaxSize, int stringMaxChars, long budget, long timeoutMillis) {

    static final String COST_MESSAGE = "Template cost limit exceeded";

    /**
     * Budget du rendu en cours sur ce fil. Il n'est pas une variable du contexte : un gabarit ne peut ni le lire ni le
     * masquer ({@code th:with}, {@code th:each}).
     */
    private static final ThreadLocal<Budget> CURRENT = new ThreadLocal<>();

    /** Ouvre un budget neuf pour un rendu ; à fermer (try-with-resources) à la fin du rendu. */
    public Scope begin() {
        Budget previous = CURRENT.get();
        CURRENT.set(new Budget(budget, System.nanoTime() + timeoutMillis * 1_000_000L));
        return () -> {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        };
    }

    /** Fin d'un rendu. */
    public interface Scope extends AutoCloseable {
        @Override
        void close();
    }

    /** Débite le budget du rendu en cours (sans effet hors d'un rendu) et contrôle l'échéance. */
    static void charge(long cost) {
        Budget current = CURRENT.get();
        if (current == null) {
            return;
        }
        current.remaining -= cost;
        if (current.remaining < 0) {
            throw new TemplateProcessingException(COST_MESSAGE + " (evaluation budget exhausted)");
        }
        if (System.nanoTime() - current.deadlineNanos > 0) {
            throw new TemplateProcessingException(COST_MESSAGE + " (render time limit exceeded)");
        }
    }

    private static final class Budget {
        private long remaining;
        private final long deadlineNanos;

        Budget(long remaining, long deadlineNanos) {
            this.remaining = remaining;
            this.deadlineNanos = deadlineNanos;
        }
    }
}
