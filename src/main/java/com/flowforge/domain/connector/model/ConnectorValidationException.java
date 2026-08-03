package com.flowforge.domain.connector.model;

public class ConnectorValidationException extends RuntimeException {

    public ConnectorValidationException(String message) {
        super(message);
    }
}
