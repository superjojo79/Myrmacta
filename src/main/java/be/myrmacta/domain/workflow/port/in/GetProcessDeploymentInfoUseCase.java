package be.myrmacta.domain.workflow.port.in;

import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import java.util.Optional;

public interface GetProcessDeploymentInfoUseCase {

    Optional<ProcessDeploymentInfo> getDeploymentInfo(String processDefinitionKey);
}
