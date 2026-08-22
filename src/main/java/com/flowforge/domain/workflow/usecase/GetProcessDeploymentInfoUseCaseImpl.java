package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;
import com.flowforge.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import java.util.Objects;
import java.util.Optional;

public final class GetProcessDeploymentInfoUseCaseImpl implements GetProcessDeploymentInfoUseCase {

    private final String tenantId;
    private final WorkflowDeploymentPort deploymentPort;

    public GetProcessDeploymentInfoUseCaseImpl(String tenantId, WorkflowDeploymentPort deploymentPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
    }

    @Override
    public Optional<ProcessDeploymentInfo> getDeploymentInfo(String processDefinitionKey) {
        return deploymentPort.findDeploymentInfo(tenantId, processDefinitionKey);
    }
}
