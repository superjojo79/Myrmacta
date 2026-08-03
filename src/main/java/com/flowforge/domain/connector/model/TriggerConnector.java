package com.flowforge.domain.connector.model;

import java.util.List;

public interface TriggerConnector {

    List<WorkflowTriggerEvent> onEvent(ConnectorInstanceData instance, Object rawPayload);
}
