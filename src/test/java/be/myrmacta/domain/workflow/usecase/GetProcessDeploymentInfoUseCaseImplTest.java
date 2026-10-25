package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.ProcessDeploymentInfo;
import be.myrmacta.domain.workflow.port.out.WorkflowDeploymentPort;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GetProcessDeploymentInfoUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final WorkflowDeploymentPort deploymentPort = mock(WorkflowDeploymentPort.class);
    private final GetProcessDeploymentInfoUseCaseImpl useCase =
            new GetProcessDeploymentInfoUseCaseImpl(TENANT, deploymentPort);

    @Test
    void getDeploymentInfo_keyAlreadyDeployed_returnsInfo() {
        ProcessDeploymentInfo info = new ProcessDeploymentInfo("testProcess", 3, 12);
        when(deploymentPort.findDeploymentInfo(TENANT, "testProcess")).thenReturn(Optional.of(info));

        assertThat(useCase.getDeploymentInfo("testProcess")).contains(info);
    }

    @Test
    void getDeploymentInfo_keyNeverDeployed_returnsEmpty() {
        when(deploymentPort.findDeploymentInfo(TENANT, "neverDeployed")).thenReturn(Optional.empty());

        assertThat(useCase.getDeploymentInfo("neverDeployed")).isEmpty();
    }
}
