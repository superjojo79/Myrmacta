package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import be.myrmacta.domain.workflow.port.in.GetProcessDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
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
