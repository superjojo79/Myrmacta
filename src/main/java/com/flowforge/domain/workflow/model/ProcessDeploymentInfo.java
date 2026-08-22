package com.flowforge.domain.workflow.model;

public record ProcessDeploymentInfo(
        String processDefinitionKey,
        int latestVersion,
        long activeInstanceCount
) {
}
