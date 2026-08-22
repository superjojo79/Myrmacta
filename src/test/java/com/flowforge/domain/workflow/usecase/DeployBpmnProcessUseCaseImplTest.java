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
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeployBpmnProcessUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final byte[] BPMN_BYTES = "<bpmn/>".getBytes(StandardCharsets.UTF_8);

    private final WorkflowDeploymentPort deploymentPort = mock(WorkflowDeploymentPort.class);
    private final WorkflowTypeConfigRepository configRepository = mock(WorkflowTypeConfigRepository.class);
    private final BpmnParserPort bpmnParserPort = mock(BpmnParserPort.class);
    private final DeployBpmnProcessUseCaseImpl useCase =
            new DeployBpmnProcessUseCaseImpl(TENANT, deploymentPort, configRepository, bpmnParserPort);

    @Test
    void deploy_validBpmnWithKnownKey_delegatesToDeploymentPort() {
        when(bpmnParserPort.extractProcessDefinitionKey(BPMN_BYTES)).thenReturn("testProcess");
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
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
}
