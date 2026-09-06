package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP-facing view of a {@link ConnectorInstanceData} with secret config values masked — mirrors
 * {@code infrastructure.web.connector.ConnectorApiDtos.ConnectorResponse}'s masking (same
 * {@link ManageConnectorInstanceUseCaseImpl#isSecretKey}/{@code SECRET_MASK}), reused rather than
 * duplicated, so a secret is never serialized in clear by {@code list_connector_instances}/
 * {@code get_connector_instance} (extension-mcp-flux-metier, D-MCP-EXT-SECRET).
 */
public record ConnectorInstanceMcpView(
        UUID id,
        String tenantId,
        String name,
        String connectorType,
        boolean enabled,
        Map<String, Object> config) {

    public static ConnectorInstanceMcpView from(ConnectorInstanceData data) {
        return new ConnectorInstanceMcpView(
                data.id(), data.tenantId(), data.name(), data.connectorType(), data.enabled(),
                maskSecrets(data.config()));
    }

    private static Map<String, Object> maskSecrets(Map<String, Object> config) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            if (ManageConnectorInstanceUseCaseImpl.isSecretKey(entry.getKey()) && entry.getValue() != null) {
                masked.put(entry.getKey(), ManageConnectorInstanceUseCaseImpl.SECRET_MASK);
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(masked);
    }
}
