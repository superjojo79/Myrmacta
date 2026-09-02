package com.flowforge.domain.connector.port.in;

import com.flowforge.domain.connector.model.ConnectorInstanceData;

public interface CallbackConnectorRuntime {

    void onIncomingCallback(ConnectorInstanceData instance, Object rawPayload);
}
