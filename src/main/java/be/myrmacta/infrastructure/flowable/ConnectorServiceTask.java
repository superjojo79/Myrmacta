package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.ConnectorCallContext;
import be.myrmacta.domain.connector.port.in.ExecuteConnectorUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * BPMN service task delegate — reads {@code connectorInstanceId} (+ optional
 * {@code connectorInput}) and invokes {@link ExecuteConnectorUseCase} (DD-SIGNAL-HOOK).
 */
@Component("connectorServiceTask")
class ConnectorServiceTask implements JavaDelegate {

    private final ExecuteConnectorUseCase executeConnectorUseCase;
    private final TenantProperties tenantProperties;

    ConnectorServiceTask(
            ExecuteConnectorUseCase executeConnectorUseCase, TenantProperties tenantProperties) {
        this.executeConnectorUseCase = executeConnectorUseCase;
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void execute(DelegateExecution execution) {
        Object instanceIdVar = DeployedActivityParameters.get(execution, "connectorInstanceId");
        if (instanceIdVar == null) {
            throw new IllegalArgumentException("connectorInstanceId process variable is required");
        }
        UUID instanceId = UUID.fromString(instanceIdVar.toString());

        String tenantId = tenantProperties.id();

        @SuppressWarnings("unchecked")
        Map<String, Object> input = execution.getVariable("connectorInput") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        Map<String, Object> result = executeConnectorUseCase.execute(
                instanceId, tenantId, input, ConnectorCallContext.process(dossierId(execution)));
        if (result != null && !result.isEmpty()) {
            execution.setVariable("connectorResult", result);
        }
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
