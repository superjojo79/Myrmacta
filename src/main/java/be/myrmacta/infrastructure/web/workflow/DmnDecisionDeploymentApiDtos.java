package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;

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
