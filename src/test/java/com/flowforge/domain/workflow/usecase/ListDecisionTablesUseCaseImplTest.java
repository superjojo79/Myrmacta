package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListDecisionTablesUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private final DmnDeploymentPort deploymentPort = mock(DmnDeploymentPort.class);
    private final ListDecisionTablesUseCaseImpl useCase = new ListDecisionTablesUseCaseImpl(TENANT, deploymentPort);

    @Test
    void list_delegatesToPortForTheCurrentTenant() {
        List<DecisionTableDeploymentInfo> infos =
                List.of(new DecisionTableDeploymentInfo("decisionA", 1), new DecisionTableDeploymentInfo("decisionB", 2));
        when(deploymentPort.findAllLatestDeployments(TENANT)).thenReturn(infos);

        assertThat(useCase.list()).isEqualTo(infos);
    }

    @Test
    void list_noDeployments_returnsEmptyList() {
        when(deploymentPort.findAllLatestDeployments(TENANT)).thenReturn(List.of());

        assertThat(useCase.list()).isEmpty();
    }
}
