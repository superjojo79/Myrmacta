package com.flowforge.domain.connector.model;

import java.util.List;
import java.util.Map;

public interface TriggerConnector {

    List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Map<String, Object> rawPayload);
}
