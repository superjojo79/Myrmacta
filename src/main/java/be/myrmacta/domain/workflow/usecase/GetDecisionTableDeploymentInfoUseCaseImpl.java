package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.in.GetDecisionTableDeploymentInfoUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import java.util.Objects;
import java.util.Optional;

public final class GetDecisionTableDeploymentInfoUseCaseImpl implements GetDecisionTableDeploymentInfoUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;

    public GetDecisionTableDeploymentInfoUseCaseImpl(String tenantId, DmnDeploymentPort deploymentPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
    }

    @Override
    public Optional<DecisionTableDeploymentInfo> getDeploymentInfo(String decisionKey) {
        return deploymentPort.findDeploymentInfo(tenantId, decisionKey);
    }
}
