package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import java.util.Optional;

public interface GetDecisionTableDeploymentInfoUseCase {

    Optional<DecisionTableDeploymentInfo> getDeploymentInfo(String decisionKey);
}
