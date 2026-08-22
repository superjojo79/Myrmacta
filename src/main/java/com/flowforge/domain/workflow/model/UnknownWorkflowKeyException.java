package com.flowforge.domain.workflow.model;

public class UnknownWorkflowKeyException extends RuntimeException {
    public UnknownWorkflowKeyException(String message) {
        super(message);
    }
}
