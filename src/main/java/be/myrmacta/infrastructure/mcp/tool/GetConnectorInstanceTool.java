package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GetConnectorInstanceTool {

    private final ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private final ConnectorCatalog connectorCatalog;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public GetConnectorInstanceTool(
            ManageConnectorInstanceUseCase manageConnectorInstanceUseCase,
            ConnectorCatalog connectorCatalog,
            TenantProperties tenantProperties,
            McpAccessContextFactory accessContextFactory) {
        this.manageConnectorInstanceUseCase =
                Objects.requireNonNull(manageConnectorInstanceUseCase, "manageConnectorInstanceUseCase");
        this.connectorCatalog = Objects.requireNonNull(connectorCatalog, "connectorCatalog");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(
            name = "get_connector_instance",
            description = "Get a connector instance by id. Secret config values (password/*secret) are "
                    + "masked, never returned in clear.")
    public ConnectorInstanceMcpView getConnectorInstance(
            @McpToolParam(required = true, description = "Connector instance id (UUID)") String id) {
        return ConnectorInstanceMcpView.from(
                manageConnectorInstanceUseCase.get(
                        UUID.fromString(id), tenantProperties.id(), accessContextFactory.currentActor()),
                connectorCatalog);
    }
}
