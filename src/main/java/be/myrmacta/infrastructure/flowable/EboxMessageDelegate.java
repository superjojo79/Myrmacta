package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;

/**
 * TODO §150 et §151 (D-EBOX-ACTION, D-EBOX-MOVE) — socle des briques eBox. Le connecteur ({@code connectorInstanceId}) est un
 * attribut littéral du BPMN, jamais une variable, et doit être de type {@code ebox-inbound}. Le message vient des variables
 * {@code eboxRegistre} et {@code eboxMessageId} posées par le connecteur au dépôt du courrier ; à défaut (process d'un dossier
 * métier ouvert par le routage), des champs de même nom du dossier courant. Le connecteur n'agit que sur un message que ce tenant
 * a pris dans cette eBox (journal). Une erreur remonte : l'étape échoue et son incident se rejoue.
 */
abstract class EboxMessageDelegate implements JavaDelegate {

    static final String CONNECTOR_TYPE = "ebox-inbound";
    static final String INSTANCE = "connectorInstanceId";
    static final String REGISTRY = "eboxRegistre";
    static final String MESSAGE = "eboxMessageId";

    private final ExecuteConnectorUseCase executeConnector;
    private final ConnectorInstanceRepository instances;
    private final DossierRepository dossiers;
    private final TenantProperties tenantProperties;

    EboxMessageDelegate(ExecuteConnectorUseCase executeConnector, ConnectorInstanceRepository instances, DossierRepository dossiers,
            TenantProperties tenantProperties) {
        this.executeConnector = Objects.requireNonNull(executeConnector, "executeConnector");
        this.instances = Objects.requireNonNull(instances, "instances");
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    /** Appelle l'opération {@code operation} du connecteur sur le message de l'exécution, avec les paramètres {@code extra}. */
    final Map<String, Object> call(DelegateExecution execution, String operation, Map<String, Object> extra) {
        String instanceAttribute = DeployedActivityParameters.getLiteral(execution, INSTANCE);
        if (instanceAttribute == null) {
            throw new IllegalArgumentException("connectorInstanceId must be a literal flowable:connectorInstanceId attribute");
        }
        UUID instanceId = UUID.fromString(instanceAttribute);
        String tenantId = tenantProperties.id();
        ConnectorInstanceData instance = instances.findByIdAndTenantId(instanceId, tenantId)
                .orElseThrow(() -> new IllegalStateException("connector instance not found: " + instanceId));
        if (!CONNECTOR_TYPE.equals(instance.connectorType())) {
            throw new IllegalStateException("connector instance " + instanceId + " is not an " + CONNECTOR_TYPE + " instance");
        }
        UUID dossierId = dossierId(execution);
        String registry = text(execution.getVariable(REGISTRY));
        String message = text(execution.getVariable(MESSAGE));
        if ((registry == null || message == null) && dossierId != null) {
            Optional<Dossier> dossier = dossiers.findById(dossierId);
            registry = dossier.map(d -> text(d.fields().get(REGISTRY))).orElse(null);
            message = dossier.map(d -> text(d.fields().get(MESSAGE))).orElse(null);
        }
        if (registry == null || message == null) {
            throw new IllegalStateException("no eBox message on this process or dossier (eboxRegistre, eboxMessageId)");
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.putAll(extra);
        input.put("operation", operation);
        input.put("registry", registry);
        input.put("messageId", message);
        Map<String, Object> result = executeConnector.execute(instanceId, tenantId, input, ConnectorCallContext.process(dossierId));
        return result == null ? Map.of() : result;
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().strip();
    }

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
