package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorCallContext;
import java.util.Map;
import java.util.UUID;

public interface ExecuteConnectorUseCase {

    /** Appel direct (REST ou M2M), sans dossier. */
    default Map<String, Object> execute(UUID instanceId, String tenantId, Map<String, Object> inputParams) {
        return execute(instanceId, tenantId, inputParams, ConnectorCallContext.direct());
    }

    Map<String, Object> execute(
            UUID instanceId, String tenantId, Map<String, Object> inputParams, ConnectorCallContext context);
}
