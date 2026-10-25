package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.in.ListDecisionTablesUseCase;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import java.util.List;
import java.util.Objects;

public final class ListDecisionTablesUseCaseImpl implements ListDecisionTablesUseCase {

    private final String tenantId;
    private final DmnDeploymentPort deploymentPort;

    public ListDecisionTablesUseCaseImpl(String tenantId, DmnDeploymentPort deploymentPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
    }

    @Override
    public List<DecisionTableDeploymentInfo> list() {
        return deploymentPort.findAllLatestDeployments(tenantId);
    }
}
