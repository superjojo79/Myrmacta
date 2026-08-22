package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;

public interface DeployBpmnProcessUseCase {

    BpmnDeploymentResult deploy(String filename, byte[] bpmnXml);
}
