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
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeployBpmnProcessUseCaseImplTest {

    private static final String TENANT = "tenant_dev";

    private static final String VALID_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn"
                         targetNamespace="http://flowforge/test">
              <process id="testProcess" isExecutable="true">
                <startEvent id="start"/>
                <endEvent id="end"/>
                <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
              </process>
            </definitions>
            """;

    private static final String MALFORMED_XML = "<not-even-xml";

    private static final String NON_BPMN_XML = "<root><child/></root>";

    private final WorkflowDeploymentPort deploymentPort = mock(WorkflowDeploymentPort.class);
    private final WorkflowTypeConfigRepository configRepository = mock(WorkflowTypeConfigRepository.class);
    private final DeployBpmnProcessUseCaseImpl useCase =
            new DeployBpmnProcessUseCaseImpl(TENANT, deploymentPort, configRepository);

    @Test
    void deploy_validBpmnWithKnownKey_delegatesToDeploymentPort() {
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.of(smokeConfig()));
        BpmnDeploymentResult expected = new BpmnDeploymentResult("testProcess", 1, "deployment-1");
        when(deploymentPort.deploy(TENANT, "diagram.bpmn20.xml", VALID_BPMN.getBytes(StandardCharsets.UTF_8)))
                .thenReturn(expected);

        BpmnDeploymentResult result = useCase.deploy("diagram.bpmn20.xml", VALID_BPMN.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void deploy_malformedXml_throwsInvalidBpmnAndNeverCallsDeploymentPort() {
        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", MALFORMED_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_wellFormedButNotBpmn_throwsInvalidBpmnAndNeverCallsDeploymentPort() {
        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", NON_BPMN_XML.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidBpmnException.class);

        verify(deploymentPort, never()).deploy(any(), any(), any());
    }

    @Test
    void deploy_unknownWorkflowKey_throwsUnknownWorkflowKeyAndNeverCallsDeploymentPort() {
        when(configRepository.findByWorkflowKey(TENANT, "testProcess")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.deploy("diagram.bpmn20.xml", VALID_BPMN.getBytes(StandardCharsets.UTF_8)))
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
