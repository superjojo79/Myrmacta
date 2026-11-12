package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.DmnXmlVersions;
import be.myrmacta.domain.workflow.model.DecisionTableDeploymentResult;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.port.out.DmnDeploymentPort;
import be.myrmacta.domain.workflow.port.out.DmnDraftRepository;
import be.myrmacta.domain.workflow.port.out.DmnParserPort;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

    @Test
    void deploy_dmn11_normalizesTo13BeforeParseAndDeploy() {
        byte[] dmn11 =
                """
                <definitions xmlns="%s" id="def">
                  <decision id="eligibilite" name="E"/>
                </definitions>
                """
                        .formatted(DmnXmlVersions.DMN_11_NAMESPACE)
                        .getBytes(StandardCharsets.UTF_8);
        byte[] expected13 = DmnXmlVersions.to13(dmn11);
        when(dmnParserPort.extractDecisionKey(expected13)).thenReturn("eligibilite");
        when(deploymentPort.deploy(any(), any(), any()))
                .thenReturn(new DecisionTableDeploymentResult("eligibilite", 1, "dep-1"));

        useCase.deploy("eligibilite.dmn", dmn11);

        ArgumentCaptor<byte[]> xmlCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(deploymentPort).deploy(org.mockito.ArgumentMatchers.eq(TENANT), org.mockito.ArgumentMatchers.eq("eligibilite.dmn"), xmlCaptor.capture());
        assertThat(new String(xmlCaptor.getValue(), StandardCharsets.UTF_8))
                .contains(DmnXmlVersions.DMN_13_NAMESPACE)
                .doesNotContain(DmnXmlVersions.DMN_11_NAMESPACE);
    }
}
