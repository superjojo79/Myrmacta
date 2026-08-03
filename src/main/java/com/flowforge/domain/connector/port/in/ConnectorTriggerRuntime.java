package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;

public interface ConnectorTriggerRuntime {

    void onIncomingEvent(ConnectorInstanceData instance, Object rawPayload);
}
