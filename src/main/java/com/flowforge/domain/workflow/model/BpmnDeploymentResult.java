package com.flowforge.domain.workflow.model;

public record BpmnDeploymentResult(String processDefinitionKey, int deployedVersion, String deploymentId) {
}
