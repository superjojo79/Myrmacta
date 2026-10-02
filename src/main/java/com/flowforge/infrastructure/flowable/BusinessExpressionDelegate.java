package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.GetDossierUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.expression.model.ExpressionResult;
import com.flowforge.domain.expression.port.out.ExpressionEnginePort;
import com.flowforge.domain.workflow.palette.BlockAttributeRules;
import com.flowforge.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §75 (LC2) — brique « Évaluer une expression » : évalue la formule (attribut littéral, jamais une variable) sur un
 * document racine borné (voir {@link BusinessExpressionContext}) et pose le résultat dans {@code formulaResult}. Jamais
 * évaluée en JUEL : une passerelle compare ensuite cette variable avec la grammaire de condition sûre existante.
 *
 * <p>Résultat : booléen, nombre et texte tels quels ; objet ou liste en <b>texte JSON</b> (une variable de process ne doit
 * pas porter de structure arbitraire) ; {@code null} explicite permis. Une formule qui ne produit <b>aucune valeur</b>
 * (chemin absent) échoue avec un message clair plutôt que de laisser une passerelle router en silence sur une variable
 * absente (D-EXPR-UNDEFINED) : l'auteur borne ce cas dans l'expression (valeur par défaut).
 */
@Component("businessExpressionTask")
class BusinessExpressionDelegate implements JavaDelegate {

    private static final Logger LOG = LoggerFactory.getLogger(BusinessExpressionDelegate.class);
    private static final String DELEGATE = "businessExpressionTask";

    private final ExpressionEnginePort engine;
    private final GetDossierUseCase getDossier;
    private final DossierTypeRepository dossierTypes;
    private final TenantProperties tenantProperties;
    private final ObjectMapper objectMapper;

    BusinessExpressionDelegate(
            ExpressionEnginePort engine,
            GetDossierUseCase getDossier,
            DossierTypeRepository dossierTypes,
            TenantProperties tenantProperties,
            ObjectMapper objectMapper) {
        this.engine = engine;
        this.getDossier = getDossier;
        this.dossierTypes = dossierTypes;
        this.tenantProperties = tenantProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        String formula = DeployedActivityParameters.getLiteral(execution, "formula");
        String formulaResult = DeployedActivityParameters.getLiteral(execution, "formulaResult");
        if (formula == null || formulaResult == null) {
            throw new IllegalStateException(
                    "businessExpressionTask requires the literal attributes flowable:formula and flowable:formulaResult");
        }
        // Défense en profondeur : un BPMN déployé sans passer par le parseur (ressource classpath, import direct) ne doit
        // pas pouvoir écraser une variable réservée. Mêmes règles de forme qu'au déploiement.
        BlockAttributeRules.validate(
                execution.getCurrentActivityId(), DELEGATE, Map.of("formula", formula, "formulaResult", formulaResult));
        Map<String, Object> context = BusinessExpressionContext.forExecution(
                execution, getDossier, dossierTypes, tenantProperties, objectMapper).context();

        ExpressionResult result = engine.evaluate(formula, context);
        if (!result.defined()) {
            throw new IllegalStateException("formula of " + execution.getCurrentActivityId()
                    + " produced no value; give it a default (e.g. `a.b ? a.b : 0`)");
        }
        execution.setVariable(formulaResult, storable(result.value()));
        LOG.debug("Expression evaluated for activity {}", execution.getCurrentActivityId());
    }

    private Object storable(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return objectMapper.writeValueAsString(value);
        }
        return value;
    }
}
