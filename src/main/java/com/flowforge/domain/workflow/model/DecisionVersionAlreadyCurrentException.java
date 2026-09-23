package com.flowforge.domain.workflow.model;

public class DecisionVersionAlreadyCurrentException extends RuntimeException {

    public DecisionVersionAlreadyCurrentException(String message) {
        super(message);
    }
}
