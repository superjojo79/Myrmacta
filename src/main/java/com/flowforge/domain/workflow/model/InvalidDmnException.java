package com.flowforge.domain.workflow.model;

public class InvalidDmnException extends RuntimeException {
    public InvalidDmnException(String message) {
        super(message);
    }

    public InvalidDmnException(String message, Throwable cause) {
        super(message, cause);
    }
}
