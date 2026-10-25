package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;

public interface DeployDecisionTableUseCase {

    DecisionTableDeploymentResult deploy(String filename, byte[] dmnXml);
}
