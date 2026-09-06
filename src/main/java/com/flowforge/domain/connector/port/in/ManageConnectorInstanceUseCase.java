package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Map;
import java.util.UUID;

/**
 * {@code actor} is required on every method — mirrors the {@code @PreAuthorize} at the class
 * level of {@code ConnectorAdminController} ({@code TENANT_ADMIN}-only for every route, including
 * reads), reproduced at the domain level so an MCP caller (bypassing the REST controller) is
 * equally gated (extension-mcp-flux-metier, D5).
 */
public interface ManageConnectorInstanceUseCase {

    /**
     * Reasserts authorization without any side effect — for previews (e.g. {@code dryRun}) that
     * must never call {@link #create}/{@link #update}, but must still be gated the same way
     * (adversarial review follow-up, extension-mcp-flux-metier: {@code define_connector_instance}
     * create dryRun skipped this, unlike its own update dryRun which reasserts via {@link #get}).
     */
    void assertAuthorized(AccessContext.User actor);

    ConnectorInstanceData create(CreateCommand command, AccessContext.User actor);

    ConnectorInstanceData update(UpdateCommand command, AccessContext.User actor);

    ConnectorInstanceData get(UUID id, String tenantId, AccessContext.User actor);

    PageResult<ConnectorInstanceData> list(String tenantId, PageQuery query, AccessContext.User actor);

    void softDelete(UUID id, String tenantId, AccessContext.User actor);

    ConnectorInstanceData restore(UUID id, String tenantId, AccessContext.User actor);

    record CreateCommand(
            String tenantId,
            String name,
            String connectorType,
            boolean enabled,
            Map<String, Object> config) {}

    record UpdateCommand(
            UUID id,
            String tenantId,
            String name,
            Boolean enabled,
            Map<String, Object> config,
            boolean restore) {}
}
