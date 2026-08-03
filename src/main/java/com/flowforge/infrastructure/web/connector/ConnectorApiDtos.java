package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

record CreateConnectorRequest(
        String name, String connectorType, Boolean enabled, Map<String, Object> config) {}

record UpdateConnectorRequest(String name, Boolean enabled, Map<String, Object> config, Boolean restore) {}

record ExecuteConnectorRequest(
        String signalName, String processInstanceId, Map<String, Object> variables) {

    Map<String, Object> toInputParams() {
        Map<String, Object> params = new LinkedHashMap<>();
        if (signalName != null && !signalName.isBlank()) {
            params.put("signalName", signalName);
        }
        if (processInstanceId != null && !processInstanceId.isBlank()) {
            params.put("processInstanceId", processInstanceId);
        }
        if (variables != null) {
            params.put("variables", variables);
        }
        return params;
    }
}

record ConnectorResponse(
        UUID id,
        String tenantId,
        String name,
        String connectorType,
        boolean enabled,
        Map<String, Object> config,
        Map<String, Boolean> secretConfigured) {

    static ConnectorResponse from(ConnectorInstanceData data) {
        MaskedConfig masked = maskSecrets(data.config());
        return new ConnectorResponse(
                data.id(),
                data.tenantId(),
                data.name(),
                data.connectorType(),
                data.enabled(),
                masked.config(),
                masked.secretConfigured());
    }

    private static MaskedConfig maskSecrets(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        Map<String, Boolean> secretConfigured = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (isSecretKey(entry.getKey()) && entry.getValue() != null) {
                masked.put(entry.getKey(), ManageConnectorInstanceUseCaseImpl.SECRET_MASK);
                secretConfigured.put(entry.getKey(), true);
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return new MaskedConfig(Map.copyOf(masked), Map.copyOf(secretConfigured));
    }

    private static boolean isSecretKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("password") || k.endsWith("secret") || k.endsWith("password");
    }

    private record MaskedConfig(Map<String, Object> config, Map<String, Boolean> secretConfigured) {}
}

record ConnectorDefinitionResponse(
        String type, String label, ConnectorCapability capability, List<ConfigField> configSchema) {

    static ConnectorDefinitionResponse from(ConnectorDefinition definition) {
        return new ConnectorDefinitionResponse(
                definition.getType(),
                definition.getLabel(),
                definition.getCapability(),
                definition.getConfigSchema());
    }
}
