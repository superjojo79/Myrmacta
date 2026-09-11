package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.DecisionTableDeploymentResult;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.DmnDraftRepository;
import com.flowforge.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DeployDecisionTableUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final byte[] DMN_BYTES = "<dmn/>".getBytes(StandardCharsets.UTF_8);

    private final DmnDeploymentPort deploymentPort = mock(DmnDeploymentPort.class);
    private final DmnParserPort dmnParserPort = mock(DmnParserPort.class);
    private final DmnDraftRepository draftRepository = mock(DmnDraftRepository.class);
    private final DeployDecisionTableUseCaseImpl useCase =
            new DeployDecisionTableUseCaseImpl(TENANT, deploymentPort, dmnParserPort, draftRepository);

    @Test
    void deploy_validDmn_delegatesToDeploymentPort() {
        when(dmnParserPort.extractDecisionKey(DMN_BYTES)).thenReturn("eligibilite");
        DecisionTableDeploymentResult expected = new DecisionTableDeploymentResult("eligibilite", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "eligibilite.dmn", DMN_BYTES)).thenReturn(expected);

        DecisionTableDeploymentResult result = useCase.deploy("eligibilite.dmn", DMN_BYTES);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_invalidDmn_propagatesParserExceptionAndNeverCallsDeploymentPort() {
        when(dmnParserPort.extractDecisionKey(DMN_BYTES)).thenThrow(new InvalidDmnException("not valid DMN"));

        assertThatThrownBy(() -> useCase.deploy("eligibilite.dmn", DMN_BYTES)).isInstanceOf(InvalidDmnException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_success_deletesExistingDraftForDeployedKey() {
        when(dmnParserPort.extractDecisionKey(DMN_BYTES)).thenReturn("eligibilite");
        when(deploymentPort.deploy(TENANT, "eligibilite.dmn", DMN_BYTES))
                .thenReturn(new DecisionTableDeploymentResult("eligibilite", 1, "deployment-1"));

        useCase.deploy("eligibilite.dmn", DMN_BYTES);

        verify(draftRepository).deleteByTenantIdAndDecisionKey(TENANT, "eligibilite");
    }

    @Test
    void deploy_failure_keepsExistingDraftForKey() {
        when(dmnParserPort.extractDecisionKey(DMN_BYTES)).thenThrow(new InvalidDmnException("not valid DMN"));

        assertThatThrownBy(() -> useCase.deploy("eligibilite.dmn", DMN_BYTES)).isInstanceOf(InvalidDmnException.class);

        verify(draftRepository, never()).deleteByTenantIdAndDecisionKey(any(), any());
    }
}
