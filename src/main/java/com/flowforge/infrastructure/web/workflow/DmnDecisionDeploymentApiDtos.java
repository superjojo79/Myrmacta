package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;

record DecisionTableDeploymentInfoResponse(String decisionKey, int latestVersion) {

    static DecisionTableDeploymentInfoResponse from(DecisionTableDeploymentInfo info) {
        return new DecisionTableDeploymentInfoResponse(info.decisionKey(), info.version());
    }
}

record DecisionTableDeploymentResultResponse(String decisionKey, int deployedVersion, String deploymentId) {

    static DecisionTableDeploymentResultResponse from(DecisionTableDeploymentResult result) {
        return new DecisionTableDeploymentResultResponse(
                result.decisionKey(), result.deployedVersion(), result.deploymentId());
    }
}
