package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.DecisionTableDeploymentInfo;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetDecisionTableDeploymentInfoUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final DmnDeploymentPort deploymentPort = mock(DmnDeploymentPort.class);
    private final GetDecisionTableDeploymentInfoUseCaseImpl useCase =
            new GetDecisionTableDeploymentInfoUseCaseImpl(TENANT, deploymentPort);

    @Test
    void getDeploymentInfo_keyAlreadyDeployed_returnsInfo() {
        DecisionTableDeploymentInfo info = new DecisionTableDeploymentInfo("testDecision", 3);
        when(deploymentPort.findDeploymentInfo(TENANT, "testDecision")).thenReturn(Optional.of(info));

        assertThat(useCase.getDeploymentInfo("testDecision")).contains(info);
    }

    @Test
    void getDeploymentInfo_keyNeverDeployed_returnsEmpty() {
        when(deploymentPort.findDeploymentInfo(TENANT, "neverDeployed")).thenReturn(Optional.empty());

        assertThat(useCase.getDeploymentInfo("neverDeployed")).isEmpty();
    }
}
