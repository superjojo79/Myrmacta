package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.util.Map;
import java.util.UUID;

public interface ManageConnectorInstanceUseCase {

    ConnectorInstanceData create(CreateCommand command);

    ConnectorInstanceData update(UpdateCommand command);

    ConnectorInstanceData get(UUID id, String tenantId);

    PageResult<ConnectorInstanceData> list(String tenantId, PageQuery query);

    void softDelete(UUID id, String tenantId);

    ConnectorInstanceData restore(UUID id, String tenantId);

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
