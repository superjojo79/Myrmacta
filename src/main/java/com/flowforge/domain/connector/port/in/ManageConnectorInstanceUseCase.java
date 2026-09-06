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
