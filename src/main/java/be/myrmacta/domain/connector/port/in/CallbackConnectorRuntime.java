package be.myrmacta.domain.connector.port.in;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;

public interface CallbackConnectorRuntime {

    void onIncomingCallback(ConnectorInstanceData instance, Object rawPayload);
}
