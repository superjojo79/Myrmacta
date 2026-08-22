package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;

record ProcessDeploymentInfoResponse(String processDefinitionKey, int latestVersion, long activeInstanceCount) {

    static ProcessDeploymentInfoResponse from(ProcessDeploymentInfo info) {
        return new ProcessDeploymentInfoResponse(
                info.processDefinitionKey(), info.latestVersion(), info.activeInstanceCount());
    }
}

record BpmnDeploymentResultResponse(String processDefinitionKey, int deployedVersion, String deploymentId) {

    static BpmnDeploymentResultResponse from(BpmnDeploymentResult result) {
        return new BpmnDeploymentResultResponse(
                result.processDefinitionKey(), result.deployedVersion(), result.deploymentId());
    }
}
