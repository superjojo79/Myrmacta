package com.flowforge.domain.expression.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.expression.model.ExpressionResult;
import java.util.Map;

public interface EvaluateExpressionUseCase {

    /** Évaluation à blanc sur un contexte d'exemple fourni par l'appelant : aucun effet de bord, aucune donnée du tenant lue. */
    ExpressionResult evaluate(AccessContext.User actor, String expression, Map<String, Object> context);
}
