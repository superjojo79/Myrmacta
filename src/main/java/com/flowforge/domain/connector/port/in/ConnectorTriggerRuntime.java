package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import java.util.Map;

public interface ConnectorTriggerRuntime {

    void onIncomingEvent(ConnectorInstanceData instance, Map<String, Object> rawPayload);
}
