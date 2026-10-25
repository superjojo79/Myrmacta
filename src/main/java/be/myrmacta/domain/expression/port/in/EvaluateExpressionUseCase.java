package be.myrmacta.domain.expression.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.expression.model.ExpressionResult;
import java.util.Map;

public interface EvaluateExpressionUseCase {

    /** Évaluation à blanc sur un contexte d'exemple fourni par l'appelant : aucun effet de bord, aucune donnée du tenant lue. */
    ExpressionResult evaluate(AccessContext.User actor, String expression, Map<String, Object> context);
}
