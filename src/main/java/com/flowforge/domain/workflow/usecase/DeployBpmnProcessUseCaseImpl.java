package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.UnknownWorkflowKeyException;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;

public final class DeployBpmnProcessUseCaseImpl implements DeployBpmnProcessUseCase {

    private final String tenantId;
    private final WorkflowDeploymentPort deploymentPort;
    private final WorkflowTypeConfigRepository configRepository;

    public DeployBpmnProcessUseCaseImpl(
            String tenantId, WorkflowDeploymentPort deploymentPort, WorkflowTypeConfigRepository configRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
    }

    @Override
    public BpmnDeploymentResult deploy(String filename, byte[] bpmnXml) {
        String processDefinitionKey = extractProcessDefinitionKey(bpmnXml);
        configRepository.findByWorkflowKey(tenantId, processDefinitionKey)
                .orElseThrow(() -> new UnknownWorkflowKeyException(
                        "No WorkflowTypeConfig found for key: " + processDefinitionKey));
        return deploymentPort.deploy(tenantId, filename, bpmnXml);
    }

    private String extractProcessDefinitionKey(byte[] bpmnXml) {
        BpmnModel model;
        try {
            model = new BpmnXMLConverter().convertToBpmnModel(() -> new ByteArrayInputStream(bpmnXml), true, false);
        } catch (Exception ex) {
            throw new InvalidBpmnException("File is not a valid BPMN 2.0 document: " + ex.getMessage(), ex);
        }
        Process mainProcess = model.getMainProcess();
        if (mainProcess == null || mainProcess.getId() == null || mainProcess.getId().isBlank()) {
            throw new InvalidBpmnException("BPMN document does not define a process with an id");
        }
        return mainProcess.getId();
    }
}
