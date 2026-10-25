package be.myrmacta.domain.workflow.model;

public record BpmnDeploymentResult(String processDefinitionKey, int deployedVersion, String deploymentId) {
}
