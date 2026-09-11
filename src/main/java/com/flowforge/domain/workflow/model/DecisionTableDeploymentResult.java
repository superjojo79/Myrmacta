package com.flowforge.domain.workflow.model;

public record DecisionTableDeploymentResult(String decisionKey, int deployedVersion, String deploymentId) {
}
