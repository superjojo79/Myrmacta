package com.flowforge.domain.connector.model;

public interface CallbackConnector {

    CallbackResolution resolve(ConnectorInstanceData instance, Object rawPayload);
}
