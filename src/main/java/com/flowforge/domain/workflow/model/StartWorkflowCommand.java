package com.flowforge.domain.workflow.model;

public record StartWorkflowCommand(String processKey, String tenantId) {
}
