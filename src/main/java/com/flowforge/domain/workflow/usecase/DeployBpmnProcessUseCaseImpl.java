package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.UnknownWorkflowKeyException;
import com.flowforge.domain.workflow.port.in.DeployBpmnProcessUseCase;
import com.flowforge.domain.workflow.port.out.BpmnDraftRepository;
import com.flowforge.domain.workflow.port.out.BpmnParserPort;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.Objects;

public final class DeployBpmnProcessUseCaseImpl implements DeployBpmnProcessUseCase {

    private final String tenantId;
    private final WorkflowDeploymentPort deploymentPort;
    private final WorkflowTypeConfigRepository configRepository;
    private final BpmnParserPort bpmnParserPort;
    private final BpmnDraftRepository draftRepository;

    public DeployBpmnProcessUseCaseImpl(
            String tenantId,
            WorkflowDeploymentPort deploymentPort,
            WorkflowTypeConfigRepository configRepository,
            BpmnParserPort bpmnParserPort,
            BpmnDraftRepository draftRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.deploymentPort = Objects.requireNonNull(deploymentPort, "deploymentPort");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
        this.bpmnParserPort = Objects.requireNonNull(bpmnParserPort, "bpmnParserPort");
        this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    }

    @Override
    public BpmnDeploymentResult deploy(String filename, byte[] bpmnXml) {
        String processDefinitionKey = bpmnParserPort.extractProcessDefinitionKey(bpmnXml);
        configRepository.findByWorkflowKey(tenantId, processDefinitionKey)
                .orElseThrow(() -> new UnknownWorkflowKeyException(
                        "No WorkflowTypeConfig found for key: " + processDefinitionKey));
        BpmnDeploymentResult result = deploymentPort.deploy(tenantId, filename, bpmnXml);
        // D-DESIGN-DRAFT-LIFECYCLE: a successful deployment retires the draft for this key --
        // avoids a stale draft silently diverging from what is actually deployed.
        draftRepository.deleteByTenantIdAndProcessDefinitionKey(tenantId, processDefinitionKey);
        return result;
    }
}
