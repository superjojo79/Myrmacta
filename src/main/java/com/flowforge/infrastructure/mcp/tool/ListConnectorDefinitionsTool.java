package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.connector.model.ConnectorDefinition;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Reads the static, tenant-agnostic connector type catalog directly from {@link ConnectorCatalog}
 * — same source as {@code ConnectorDefinitionController} (REST), no dedicated use case (design D4).
 */
@Component
public class ListConnectorDefinitionsTool {

    private final ConnectorCatalog catalog;

    public ListConnectorDefinitionsTool(ConnectorCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    @McpTool(
            name = "list_connector_definitions",
            description = "List the catalog of connector types available for define_connector_instance "
                    + "(type, capability, config schema — no instance data).")
    public PageResult<ConnectorDefinition> listConnectorDefinitions(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        var sorted = catalog.listAll().stream().sorted(Comparator.comparing(ConnectorDefinition::getType)).toList();
        return PageResult.slice(sorted, query);
    }
}
