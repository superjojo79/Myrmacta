package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;
import java.util.Optional;

public interface GetProcessDeploymentInfoUseCase {

    Optional<ProcessDeploymentInfo> getDeploymentInfo(String processDefinitionKey);
}
