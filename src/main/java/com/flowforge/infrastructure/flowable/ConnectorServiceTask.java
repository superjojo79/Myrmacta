package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.connector.port.in.ExecuteConnectorUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
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
        Object instanceIdVar = execution.getVariable("connectorInstanceId");
        if (instanceIdVar == null) {
            throw new IllegalArgumentException("connectorInstanceId process variable is required");
        }
        UUID instanceId = UUID.fromString(instanceIdVar.toString());

        Object tenantOverride = execution.getVariable("tenantId");
        String tenantId = tenantOverride == null
                ? tenantProperties.id()
                : tenantOverride.toString();

        @SuppressWarnings("unchecked")
        Map<String, Object> input = execution.getVariable("connectorInput") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        Map<String, Object> result = executeConnectorUseCase.execute(instanceId, tenantId, input);
        if (result != null && !result.isEmpty()) {
            execution.setVariable("connectorResult", result);
        }
    }
}
