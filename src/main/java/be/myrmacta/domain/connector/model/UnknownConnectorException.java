package be.myrmacta.domain.connector.model;

public class UnknownConnectorException extends RuntimeException {

    public UnknownConnectorException(String type) {
        super("Unknown connector type: " + type);
    }
}
