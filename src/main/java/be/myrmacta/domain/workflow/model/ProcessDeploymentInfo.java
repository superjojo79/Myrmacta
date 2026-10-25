package be.myrmacta.domain.workflow.model;

public record ProcessDeploymentInfo(
        String processDefinitionKey,
        int latestVersion,
        long activeInstanceCount
) {
}
