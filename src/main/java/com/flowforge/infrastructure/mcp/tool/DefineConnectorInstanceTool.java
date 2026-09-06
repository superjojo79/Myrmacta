package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ManageConnectorInstanceUseCase;
import com.flowforge.domain.connector.port.out.ConnectorCatalog;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * {@code define_connector_instance} : create (no {@code id}) or update (existing {@code id}) a
 * {@link ConnectorInstanceData}. Unlike {@code define_dossier_type} (keyed by a caller-chosen
 * {@code code}), a connector instance is keyed by a server-generated UUID — the create/update
 * split mirrors {@code ConnectorAdminController} (POST vs PATCH /{id}) rather than an upsert.
 * {@code dryRun=true} previews without persisting: required fields and, on create, the config
 * against the connector type's schema (via {@link ConnectorCatalog}, the same check
 * {@link ManageConnectorInstanceUseCase#create} would run) are still validated — found missing in
 * Phase 7 review and fixed, since a preview that skips validation defeats its own purpose. On
 * update, dry run confirms the instance exists and the actor is authorized (via {@code get})
 * without saving; it does not replicate the private secret-preserving merge, so a merged-config
 * validation preview is not offered there (documented, not silently incomplete).
 */
@Component
public class DefineConnectorInstanceTool {

    private final ManageConnectorInstanceUseCase manageConnectorInstanceUseCase;
    private final ConnectorCatalog connectorCatalog;
    private final TenantProperties tenantProperties;
    private final McpAccessContextFactory accessContextFactory;

    public DefineConnectorInstanceTool(
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
            name = "define_connector_instance",
            description = "Create (id absent) or update (id present) a connector instance. On create, name "
                    + "and connectorType are required (see list_connector_definitions for valid types) and "
                    + "config is validated against that type's schema. On update, name/enabled/config are "
                    + "optional partial changes — to preserve an existing secret field, omit it or submit the "
                    + "masked value (********) rather than a new value. dryRun=true previews without "
                    + "persisting.")
    public Map<String, Object> defineConnectorInstance(
            @McpToolParam(required = false, description = "Existing connector instance id (UUID); absent creates a new instance")
                    String id,
            @McpToolParam(required = false, description = "Display name (required on create)") String name,
            @McpToolParam(
                            required = false,
                            description = "Connector type, from list_connector_definitions (required on create, "
                                    + "immutable on update)")
                    String connectorType,
            @McpToolParam(required = false, description = "Whether the instance is enabled; default true on create")
                    Boolean enabled,
            @McpToolParam(
                            required = false,
                            description = "Connector config map — schema depends on connectorType (see "
                                    + "list_connector_definitions)")
                    Map<String, Object> config,
            @McpToolParam(required = false, description = "Restore a soft-deleted instance (update only)")
                    Boolean restore,
            @McpToolParam(
                            required = false,
                            description = "Validate without persisting (no save, no author stamp); default false")
                    Boolean dryRun) {
        AccessContext.User actor = accessContextFactory.currentActor();
        String tenantId = tenantProperties.id();
        boolean rawDryRun = Boolean.TRUE.equals(dryRun);

        if (id == null || id.isBlank()) {
            return defineCreate(tenantId, name, connectorType, enabled, config, rawDryRun, actor);
        }
        return defineUpdate(UUID.fromString(id), tenantId, name, enabled, config, restore, rawDryRun, actor);
    }

    private Map<String, Object> defineCreate(
            String tenantId,
            String name,
            String connectorType,
            Boolean enabled,
            Map<String, Object> config,
            boolean dryRun,
            AccessContext.User actor) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required to create a connector instance");
        }
        if (connectorType == null || connectorType.isBlank()) {
            throw new IllegalArgumentException("connectorType is required to create a connector instance");
        }
        Map<String, Object> resolvedConfig = config == null ? Map.of() : config;
        if (dryRun) {
            // Mirrors the validation ManageConnectorInstanceUseCaseImpl.create() runs, without
            // reaching it — same "duplicate for the preview only" pattern already used by
            // DefineDossierTypeTool's update dry run (checkCompatibilityUseCase).
            connectorCatalog.get(connectorType).validate(resolvedConfig);
            return result("create", true, null, null);
        }
        ConnectorInstanceData created = manageConnectorInstanceUseCase.create(
                new ManageConnectorInstanceUseCase.CreateCommand(
                        tenantId, name, connectorType, enabled == null || enabled, resolvedConfig),
                actor);
        return result("create", false, created.id(), created);
    }

    private Map<String, Object> defineUpdate(
            UUID id,
            String tenantId,
            String name,
            Boolean enabled,
            Map<String, Object> config,
            Boolean restore,
            boolean dryRun,
            AccessContext.User actor) {
        if (dryRun) {
            manageConnectorInstanceUseCase.get(id, tenantId, actor);
            return result("update", true, id, null);
        }
        ConnectorInstanceData updated = manageConnectorInstanceUseCase.update(
                new ManageConnectorInstanceUseCase.UpdateCommand(
                        id, tenantId, name, enabled, config, Boolean.TRUE.equals(restore)),
                actor);
        return result("update", false, id, updated);
    }

    private static Map<String, Object> result(String operation, boolean dryRun, UUID id, ConnectorInstanceData data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("operation", operation);
        map.put("dryRun", dryRun);
        map.put("id", id);
        map.put("connectorInstance", data == null ? null : ConnectorInstanceMcpView.from(data));
        return map;
    }
}
