package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ManageConnectorInstanceUseCase {

    ConnectorInstanceData create(CreateCommand command);

    ConnectorInstanceData update(UpdateCommand command);

    ConnectorInstanceData get(UUID id, String tenantId);

    List<ConnectorInstanceData> list(String tenantId);

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
