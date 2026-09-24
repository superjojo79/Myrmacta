package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.UnknownWorkflowKeyException;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeployBpmnProcessUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final byte[] BPMN_BYTES = "<bpmn/>".getBytes(StandardCharsets.UTF_8);

    private final WorkflowDeploymentPort deploymentPort = mock(WorkflowDeploymentPort.class);
    private final WorkflowTypeConfigRepository configRepository = mock(WorkflowTypeConfigRepository.class);
    private final BpmnParserPort bpmnParserPort = mock(BpmnParserPort.class);
    private final BpmnDraftRepository draftRepository = mock(BpmnDraftRepository.class);
    private final DmnDeploymentPort dmnDeploymentPort = mock(DmnDeploymentPort.class);
    private final com.flowforge.domain.workflow.portal.port.out.PortalAvailabilityPort portalAvailability =
            mock(com.flowforge.domain.workflow.portal.port.out.PortalAvailabilityPort.class);
    private final DeployBpmnProcessUseCaseImpl useCase = new DeployBpmnProcessUseCaseImpl(
            TENANT, deploymentPort, configRepository, bpmnParserPort, draftRepository, dmnDeploymentPort,
            portalAvailability);

    @Test
    void deploy_validBpmnWithKnownKey_delegatesToDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        BpmnDeploymentResult result = useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_referencedDecisionKeyNotDeployed_throwsUnknownDecisionKeyAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(UnknownDecisionKeyException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_referencedDecisionKeyDeployed_delegatesToDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of("eligibilite"));
        when(dmnDeploymentPort.findDeploymentInfo(TENANT, "eligibilite"))
                .thenReturn(Optional.of(new com.flowforge.domain.workflow.model.DecisionTableDeploymentInfo(
                        "eligibilite", 1)));
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        BpmnDeploymentResult result = useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_invalidBpmn_propagatesParserExceptionAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES))
                .thenThrow(new InvalidBpmnException("not valid BPMN"));

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(InvalidBpmnException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
        verify(configRepository, never()).findByWorkflowKey(any(), any());
    }

    @Test
    void deploy_unknownWorkflowKey_throwsUnknownWorkflowKeyAndNeverCallsDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(UnknownWorkflowKeyException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_success_deletesExistingDraftForDeployedKey() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES))
                .thenReturn(new BpmnDeploymentResult("testProcess", 1, "deployment-1"));

        useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES);

        verify(draftRepository).deleteByTenantIdAndProcessDefinitionKey(TENANT, "testProcess");
    }

    @Test
    void deploy_failure_keepsExistingDraftForKey() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES))
                .thenThrow(new InvalidBpmnException("not valid BPMN"));

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(InvalidBpmnException.class);

        verify(draftRepository, never()).deleteByTenantIdAndProcessDefinitionKey(any(), any());
    }

    private static WorkflowTypeConfig smokeConfig() {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                "testProcess",
                "Test",
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of());
    }

    private static com.flowforge.domain.workflow.portal.model.PortalTaskDefinition externalTask(Integer ttlDays) {
        return new com.flowforge.domain.workflow.portal.model.PortalTaskDefinition(
                "tiers", "avis",
                new com.flowforge.domain.workflow.portal.model.PortalRecipientSource(
                        com.flowforge.domain.workflow.portal.model.PortalRecipientSource.Kind.DOSSIER_FIELD, "email"),
                ttlDays);
    }

    @Test
    void deploy_externalTaskWhilePortalDisabled_isRejectedAndNeverDeployed() {
        // T-DEPLOY-1 (TODO §68, D-PORTAL-ACTIVATION)
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(null)));
        when(portalAvailability.isPortalEnabled()).thenReturn(false);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(com.flowforge.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portal");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_externalTaskWhilePortalEnabled_isDeployed() {
        // T-DEPLOY-2
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractReferencedDecisionKeys(BPMN_BYTES)).thenReturn(Set.of());
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(30)));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", BPMN_BYTES)).thenReturn(expected);

        assertThat(useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES)).isEqualTo(expected);
    }

    @Test
    void deploy_externalTaskTtlAboveTenantCeiling_isRejected() {
        // T-PARSE-4 (plafond tenant, vérifié au déploiement : le parseur ne connaît pas la config)
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        when(bpmnParserPort.extractPortalTasks(BPMN_BYTES)).thenReturn(java.util.List.of(externalTask(31)));
        when(portalAvailability.isPortalEnabled()).thenReturn(true);
        when(portalAvailability.maxLinkTtlDays()).thenReturn(30);

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", BPMN_BYTES))
                .isInstanceOf(com.flowforge.domain.workflow.model.InvalidBpmnException.class)
                .hasMessageContaining("portalLinkTtlDays");
        verify(deploymentPort, never()).deploy(any(), any(), any());
    }
}
