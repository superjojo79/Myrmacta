package com.flowforge.domain.connector.port.in;

import java.util.Map;
import java.util.UUID;

public interface ExecuteConnectorUseCase {

    Map<String, Object> execute(UUID instanceId, String tenantId, Map<String, Object> inputParams);
}
