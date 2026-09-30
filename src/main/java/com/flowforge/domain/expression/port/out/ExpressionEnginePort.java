package com.flowforge.domain.expression.port.out;

import com.flowforge.domain.expression.model.ExpressionResult;
import java.util.Map;

/**
 * Moteur d'expressions métier borné (TODO §75, LC2). Jamais évalué en JUEL ; sans accès au serveur (ni fichier, ni réseau,
 * ni classe Java) ; mêmes bornes en validation et en exécution. Le contexte est une structure JSON (maps, listes,
 * scalaires) fournie par l'appelant : le moteur n'y ajoute que la clé {@code now} (horloge injectée, déterministe en test).
 */
public interface ExpressionEnginePort {

    /** @throws com.flowforge.domain.expression.model.InvalidExpressionException si l'expression est refusée */
    void validate(String expression);

    /**
     * @throws com.flowforge.domain.expression.model.InvalidExpressionException expression refusée
     * @throws com.flowforge.domain.expression.model.ExpressionEvaluationException erreur ou borne dépassée à l'exécution
     */
    ExpressionResult evaluate(String expression, Map<String, Object> context);
}
