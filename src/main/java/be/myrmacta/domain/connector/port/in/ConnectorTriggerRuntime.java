package be.myrmacta.domain.connector.port.in;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;

public interface ConnectorTriggerRuntime {

    void onIncomingEvent(ConnectorInstanceData instance, Object rawPayload);
}
