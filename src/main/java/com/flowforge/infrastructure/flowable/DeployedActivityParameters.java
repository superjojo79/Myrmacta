package com.flowforge.infrastructure.flowable;

import java.util.Collection;
import java.util.Map;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.delegate.DelegateExecution;

/**
 * TODO §73 (palette-metier-bpmn) — lecture des paramètres d'une brique par un delegate :
 * <ol>
 *   <li>l'<b>attribut littéral</b> {@code flowable:<nom>} de l'activité courante, lu dans le BPMN <em>déployé</em>
 *       (validé au déploiement, jamais évalué en UEL) : ce que le concepteur a fixé n'est pas surchargeable par une
 *       variable saisie à l'exécution (revue adversariale I1 : une variable de complétion de tâche, contrôlée par
 *       l'utilisateur, ne doit pas pouvoir remplacer le statut, le connecteur ou le destinataire choisis) ;</li>
 *   <li>sinon la <b>variable de process</b> de même nom, si elle est non nulle et non vide — comportement d'avant pour
 *       tout diagramme sans attribut ;</li>
 *   <li>sinon {@code null}.</li>
 * </ol>
 * La valeur de l'attribut est nettoyée de ses espaces de bord (le déploiement refuse d'ailleurs les valeurs qui en
 * portent : forme, existence et exécution voient la même chaîne).
 */
final class DeployedActivityParameters {

    static final String FLOWABLE_NAMESPACE = "http://flowable.org/bpmn";

    private DeployedActivityParameters() {}

    static Object get(DelegateExecution execution, String name) {
        FlowElement element = execution.getCurrentFlowElement();
        if (element != null) {
            String literal = element.getAttributeValue(FLOWABLE_NAMESPACE, name);
            if (literal != null && !literal.isBlank()) {
                return literal.strip();
            }
        }
        Object variable = execution.getVariable(name);
        return isEmpty(variable) ? null : variable;
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || value.toString().isBlank()
                || (value instanceof Collection<?> c && c.isEmpty())
                || (value instanceof Map<?, ?> m && m.isEmpty());
    }

    static String getString(DelegateExecution execution, String name) {
        Object value = get(execution, name);
        return value == null ? null : value.toString();
    }
}
