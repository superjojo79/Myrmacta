package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class ListConnectorInstancesTool {

    private final ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private final ConnectorCatalog connectorCatalog;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public ListConnectorInstancesTool(
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
            name = "list_connector_instances",
            description = "List configured connector instances, paginated. Secret config values "
                    + "(password/*secret) are masked, never returned in clear.")
    public PageResult<ConnectorInstanceMcpView> listConnectorInstances(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        return manageConnectorInstanceUseCase
                .list(tenantProperties.id(), query, accessContextFactory.currentActor())
                .map(data -> ConnectorInstanceMcpView.from(data, connectorCatalog));
    }
}
