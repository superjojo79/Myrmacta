package be.myrmacta.domain.connector.model;

import java.util.UUID;

public class ConnectorInstanceNotFoundException extends RuntimeException {

    public ConnectorInstanceNotFoundException(UUID id) {
        super("Connector instance not found: " + id);
    }
}
