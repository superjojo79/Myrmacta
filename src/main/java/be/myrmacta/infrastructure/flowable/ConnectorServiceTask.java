package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.expression.model.ExpressionResult;
import be.myrmacta.domain.expression.port.out.ExpressionEnginePort;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.palette.BlockAttributeRules;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * BPMN service task delegate — reads {@code connectorInstanceId} (+ optional
 * {@code connectorInput}) and invokes {@link ExecuteConnectorUseCase} (DD-SIGNAL-HOOK).
 *
 * <p>TODO §112 (D-ORCH-REQUEST) : l'attribut <b>littéral</b> {@code flowable:connectorRequest} — une expression métier
 * bornée (LC2), validée au déploiement et revalidée ici — produit l'entrée du connecteur à partir du dossier (champs
 * déclarés non sensibles), du dernier {@code connectorResult} et des variables scalaires. Il prime sur la variable
 * {@code connectorInput}, qu'un utilisateur peut poser à la complétion d'une tâche ; il n'est jamais lu depuis une
 * variable.
 */
@Component("connectorServiceTask")
class ConnectorServiceTask implements JavaDelegate {

    private static final String DELEGATE = "connectorServiceTask";
    static final String CONNECTOR_REQUEST = "connectorRequest";

    private final ExecuteConnectorUseCase executeConnectorUseCase;
    private final TenantProperties tenantProperties;
    private final ExpressionEnginePort expressionEngine;
    private final GetDossierUseCase getDossier;
    private final DossierTypeRepository dossierTypes;
    private final ObjectMapper objectMapper;

    /** Sans moteur d'expressions : un {@code connectorRequest} échoue fermé (tests du seul appel). */
    ConnectorServiceTask(ExecuteConnectorUseCase executeConnectorUseCase, TenantProperties tenantProperties) {
        this(executeConnectorUseCase, tenantProperties, null, null, null, null);
    }

    @Autowired
    ConnectorServiceTask(
            ExecuteConnectorUseCase executeConnectorUseCase,
            TenantProperties tenantProperties,
            ExpressionEnginePort expressionEngine,
            GetDossierUseCase getDossier,
            DossierTypeRepository dossierTypes,
            ObjectMapper objectMapper) {
        this.executeConnectorUseCase = executeConnectorUseCase;
        this.tenantProperties = tenantProperties;
        this.expressionEngine = expressionEngine;
        this.getDossier = getDossier;
        this.dossierTypes = dossierTypes;
        this.objectMapper = objectMapper;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object instanceIdVar = DeployedActivityParameters.get(execution, "connectorInstanceId");
        if (instanceIdVar == null) {
            throw new IllegalArgumentException("connectorInstanceId process variable is required");
        }
        UUID instanceId = UUID.fromString(instanceIdVar.toString());

        String tenantId = tenantProperties.id();

        String request = DeployedActivityParameters.getLiteral(execution, CONNECTOR_REQUEST);
        Map<String, Object> input = request != null ? evaluateRequest(execution, request) : connectorInput(execution);

        Map<String, Object> result = executeConnectorUseCase.execute(
                instanceId, tenantId, input, ConnectorCallContext.process(dossierId(execution)));
        if (result != null && !result.isEmpty()) {
            execution.setVariable("connectorResult", result);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> connectorInput(DelegateExecution execution) {
        return execution.getVariable("connectorInput") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateRequest(DelegateExecution execution, String request) {
        String activity = execution.getCurrentActivityId();
        if (expressionEngine == null) {
            throw new IllegalStateException("connectorRequest of " + activity + " cannot be evaluated on this instance");
        }
        // Défense en profondeur : un BPMN déployé sans passer par le parseur (ressource classpath, import direct) garde les
        // mêmes règles de forme et de langage qu'au déploiement.
        BlockAttributeRules.validate(activity, DELEGATE, Map.of(CONNECTOR_REQUEST, request));
        Map<String, Object> context = BusinessExpressionContext.forExecution(
                execution, getDossier, dossierTypes, tenantProperties, objectMapper).context();
        ExpressionResult result = expressionEngine.evaluate(request, context);
        if (!result.defined() || !(result.value() instanceof Map<?, ?> map)) {
            throw new IllegalStateException("connectorRequest of " + activity
                    + " must produce an object, e.g. {'method': 'POST', 'path': '/x', 'body': {...}}");
        }
        return (Map<String, Object>) map;
    }

    /**
     * TODO §101 (issue #205) : le dossier de l'exécution, variable plateforme réservée. Un process sans dossier valide
     * donne {@code null} : seuls les connecteurs qui en ont besoin ({@code document-storage}) refusent alors l'appel.
     */
    private static UUID dossierId(DelegateExecution execution) {
        Object value = execution.getVariable(WorkflowVariables.DOSSIER_ID);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
