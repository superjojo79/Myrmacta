package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.UnknownWorkflowKeyException;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.DmnDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Objects;

public final class DeployBpmnProcessUseCaseImpl implements DeployBpmnProcessUseCase {

    private final String tenantId;
    private final WorkflowDeploymentPort deploymentPort;
    private final WorkflowTypeConfigRepository configRepository;
    private final BpmnParserPort bpmnParserPort;
    private final BpmnDraftRepository draftRepository;
    private final DmnDeploymentPort dmnDeploymentPort;

    public DeployBpmnProcessUseCaseImpl(
            String tenantId,
            WorkflowDeploymentPort deploymentPort,
            WorkflowTypeConfigRepository configRepository,
            BpmnParserPort bpmnParserPort,
            BpmnDraftRepository draftRepository,
            DmnDeploymentPort dmnDeploymentPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
        this.dmnDeploymentPort = Objects.requireNonNull(dmnDeploymentPort, "dmnDeploymentPort");
    }

    @Override
    public BpmnDeploymentResult deploy(String filename, byte[] bpmnXml) {
        String processDefinitionKey = bpmnParserPort.extractProcessDefinitionKey(bpmnXml);
        configRepository.findByWorkflowKey(tenantId, processDefinitionKey)
                .orElseThrow(() -> new UnknownWorkflowKeyException(
                        "No WorkflowTypeConfig found for key: " + processDefinitionKey));
        // TODO §61 D1 -- a serviceTask flowable:type="dmn" is only accepted by the BPMN parser in
        // shape (literal decisionTableReferenceKey); the parser alone cannot know which decision
        // tables are actually deployed, so that check happens here, mirroring the
        // WorkflowTypeConfig check just above.
        for (String decisionKey : bpmnParserPort.extractReferencedDecisionKeys(bpmnXml)) {
            dmnDeploymentPort
                    .findDeploymentInfo(tenantId, decisionKey)
                    .orElseThrow(() -> new UnknownDecisionKeyException(
                            "No deployed DMN decision table found for key: " + decisionKey));
        }
        BpmnDeploymentResult result = deploymentPort.deploy(tenantId, filename, bpmnXml);
        // D-DESIGN-DRAFT-LIFECYCLE: a successful deployment retires the draft for this key --
        // avoids a stale draft silently diverging from what is actually deployed.
        draftRepository.deleteByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
        return result;
    }
}
