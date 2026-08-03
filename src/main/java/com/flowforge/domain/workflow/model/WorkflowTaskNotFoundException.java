package com.flowforge.domain.workflow.model;

public class WorkflowTaskNotFoundException extends RuntimeException {
    public WorkflowTaskNotFoundException(String message) {
        super(message);
    }
}
