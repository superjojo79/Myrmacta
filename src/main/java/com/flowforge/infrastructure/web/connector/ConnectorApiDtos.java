package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConfigField;
import com.flowforge.domain.connector.model.ConnectorCapability;
import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record CreateConnectorRequest(
        String name, String connectorType, Boolean enabled, Map<String, Object> config) {}

record UpdateConnectorRequest(String name, Boolean enabled, Map<String, Object> config, Boolean restore) {}

record ExecuteConnectorRequest(
        String signalName,
        String processInstanceId,
        Map<String, Object> variables,
        String url,
        String method,
        Object body,
        Map<String, Object> headers,
        String to,
        String subject,
        String operation,
        String storageRef,
        String content,
        String contentType) {

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
        if (url != null && !url.isBlank()) {
            params.put("url", url);
        }
        if (method != null && !method.isBlank()) {
            params.put("method", method);
        }
        if (body != null) {
            params.put("body", body);
        }
        if (headers != null) {
            params.put("headers", headers);
        }
        if (to != null && !to.isBlank()) {
            params.put("to", to);
        }
        if (subject != null && !subject.isBlank()) {
            params.put("subject", subject);
        }
        if (operation != null && !operation.isBlank()) {
            params.put("operation", operation);
        }
        if (storageRef != null && !storageRef.isBlank()) {
            params.put("storageRef", storageRef);
        }
        if (content != null && !content.isBlank()) {
            params.put("content", content);
        }
        if (contentType != null && !contentType.isBlank()) {
            params.put("contentType", contentType);
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

    static ConnectorResponse from(ConnectorInstanceData data, ConnectorCatalog catalog) {
        MaskedConfig masked = maskSecrets(data.connectorType(), data.config(), catalog);
        return new ConnectorResponse(
                data.id(),
                data.tenantId(),
                data.name(),
                data.connectorType(),
                data.enabled(),
                masked.config(),
                masked.secretConfigured());
    }

    // Reuses ManageConnectorInstanceUseCaseImpl.isSecretKey (name heuristic) plus
    // ConnectorCatalog.isPasswordField (the connector type's declared schema) instead of a
    // private name-only duplicate — a field like authHeaderValue carries a real credential but
    // doesn't match any name pattern, so the name heuristic alone missed it (adversarial review
    // follow-up, extension-mcp-flux-metier).
    private static MaskedConfig maskSecrets(String connectorType, Map<String, Object> config, ConnectorCatalog catalog) {
        Map<String, Object> masked = new LinkedHashMap<>();
        Map<String, Boolean> secretConfigured = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            boolean isSecret = ManageConnectorInstanceUseCaseImpl.isSecretKey(entry.getKey())
                    || catalog.isPasswordField(connectorType, entry.getKey());
            if (isSecret && entry.getValue() != null) {
                masked.put(entry.getKey(), ManageConnectorInstanceUseCaseImpl.SECRET_MASK);
                secretConfigured.put(entry.getKey(), true);
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return new MaskedConfig(Map.copyOf(masked), Map.copyOf(secretConfigured));
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
