package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;

public interface DeployDecisionTableUseCase {

    DecisionTableDeploymentResult deploy(String filename, byte[] dmnXml);
}
