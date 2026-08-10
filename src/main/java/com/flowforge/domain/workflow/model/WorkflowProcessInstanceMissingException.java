package com.flowforge.domain.workflow.model;

public class WorkflowProcessInstanceMissingException extends RuntimeException {
    public WorkflowProcessInstanceMissingException(String message) {
        super(message);
    }
}
