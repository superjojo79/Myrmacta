package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.connector.usecase.ManageConnectorInstanceUseCaseImpl;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MCP-facing view of a {@link ConnectorInstanceData} with secret config values masked — mirrors
 * {@code infrastructure.web.connector.ConnectorApiDtos.ConnectorResponse}'s masking (same
 * {@link ManageConnectorInstanceUseCaseImpl#isSecretKey}/{@code SECRET_MASK}), reused rather than
 * duplicated, so a secret is never serialized in clear by {@code list_connector_instances}/
 * {@code get_connector_instance} (extension-mcp-flux-metier, D-MCP-EXT-SECRET). Masking also
 * consults {@link ConnectorCatalog#isPasswordField} (the connector type's declared config schema)
 * in addition to the name heuristic — a field like {@code authHeaderValue} carries a real
 * credential but doesn't match any name pattern, so the name heuristic alone missed it (adversarial
 * review follow-up).
 */
public record ConnectorInstanceMcpView(
        UUID id,
        String tenantId,
        String name,
        String connectorType,
        boolean enabled,
        Map<String, Object> config) {

    public static ConnectorInstanceMcpView from(ConnectorInstanceData data, ConnectorCatalog catalog) {
        return new ConnectorInstanceMcpView(
                data.id(), data.tenantId(), data.name(), data.connectorType(), data.enabled(),
                maskSecrets(data.connectorType(), data.config(), catalog));
    }

    private static Map<String, Object> maskSecrets(
            String connectorType, Map<String, Object> config, ConnectorCatalog catalog) {
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : config.entrySet()) {
            boolean isSecret = ManageConnectorInstanceUseCaseImpl.isSecretKey(entry.getKey())
                    || catalog.isPasswordField(connectorType, entry.getKey());
            if (isSecret && entry.getValue() != null) {
                masked.put(entry.getKey(), ManageConnectorInstanceUseCaseImpl.SECRET_MASK);
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(masked);
    }
}
