package be.myrmacta.infrastructure.mcp.tool;

import be.myrmacta.domain.connector.model.ConnectorDefinition;
import be.myrmacta.domain.connector.port.out.ConnectorCatalog;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.infrastructure.config.I18nProperties;
import be.myrmacta.infrastructure.connector.i18n.ConnectorCatalogLocalizer;
import be.myrmacta.infrastructure.connector.i18n.LocalizedConnectorDefinition;
import java.util.Comparator;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Reads the static, tenant-agnostic connector type catalog directly from {@link ConnectorCatalog}
 * — same source as {@code ConnectorDefinitionController} (REST), no dedicated use case (design D4).
 * Libellés et aides résolus dans la langue par défaut du tenant (D-CONNECTOR-CATALOG-I18N).
 */
@Component
public class ListConnectorDefinitionsTool {

    private final ConnectorCatalog catalog;
    private final ConnectorCatalogLocalizer localizer;
    private final I18nProperties i18n;

    public ListConnectorDefinitionsTool(
            ConnectorCatalog catalog, ConnectorCatalogLocalizer localizer, I18nProperties i18n) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.localizer = Objects.requireNonNull(localizer, "localizer");
        this.i18n = Objects.requireNonNull(i18n, "i18n");
    }

    @McpTool(
            name = "list_connector_definitions",
            description = "List the catalog of connector types available for define_connector_instance "
                    + "(type, capability, config schema — no instance data).")
    public PageResult<ConnectorDefinition> listConnectorDefinitions(
            @McpToolParam(required = false, description = "Zero-based page index, default 0") Integer page,
            @McpToolParam(required = false, description = "Page size, 1-100, default 20") Integer size) {
        PageQuery query = PageQuery.of(page == null ? 0 : page, size == null ? PageQuery.DEFAULT_SIZE : size);
        var locale = i18n.locale();
        var sorted = catalog.listAll().stream()
                .sorted(Comparator.comparing(ConnectorDefinition::getType))
                .map(definition -> (ConnectorDefinition)
                        new LocalizedConnectorDefinition(definition, localizer.localize(definition, locale)))
                .toList();
        return PageResult.slice(sorted, query);
    }
}
