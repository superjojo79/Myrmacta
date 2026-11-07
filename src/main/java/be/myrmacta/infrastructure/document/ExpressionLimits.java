package be.myrmacta.infrastructure.document;

import java.util.concurrent.atomic.AtomicLong;
import org.thymeleaf.context.Context;

/**
 * Bornes de coût de l'évaluateur D-40 pour les gabarits de notification (registre §123). Les templates de document n'en
 * ont pas ({@code null}) : leur comportement ne change pas.
 *
 * @param utilityMaxSize taille maximale (éléments ou caractères) de {@code #numbers.sequence} et {@code #strings.repeat} ;
 *     plafond des chiffres demandés à {@code #numbers.format*} ; {@code #strings.randomAlphanumeric} est refusé
 * @param budget budget d'évaluations par rendu (voir {@link #newBudget(Context)})
 */
public record ExpressionLimits(int utilityMaxSize, long budget) {

    /** Variable de contexte qui porte le budget restant du rendu en cours (le compteur est propre à un rendu). */
    static final String BUDGET_VARIABLE = "myrmacta$renderBudget";

    /** Installe un budget neuf dans le contexte d'un rendu ; à appeler après avoir posé le modèle. */
    public void newBudget(Context context) {
        context.setVariable(BUDGET_VARIABLE, new AtomicLong(budget));
    }
}
