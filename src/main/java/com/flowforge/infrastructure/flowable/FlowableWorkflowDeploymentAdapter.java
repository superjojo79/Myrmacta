package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.model.BpmnDeploymentResult;
import com.flowforge.domain.workflow.model.ProcessDeploymentInfo;
import com.flowforge.domain.workflow.port.out.WorkflowDeploymentPort;
import java.util.Objects;
import java.util.Optional;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowDeploymentAdapter implements WorkflowDeploymentPort {

    private static final String[] BPMN_RESOURCE_SUFFIXES = {".bpmn20.xml", ".bpmn", ".xml"};

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    FlowableWorkflowDeploymentAdapter(RepositoryService repositoryService, RuntimeService runtimeService) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
    }

    @Override
    public Optional<ProcessDeploymentInfo> findDeploymentInfo(String tenantId, String processDefinitionKey) {
        ProcessDefinition latest = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .processDefinitionTenantId(tenantId)
                .latestVersion()
                .singleResult();
        if (latest == null) {
            return Optional.empty();
        }
        long activeInstanceCount = runtimeService.createProcessInstanceQuery()
                .processDefinitionKey(processDefinitionKey)
                .processInstanceTenantId(tenantId)
                .count();
        return Optional.of(new ProcessDeploymentInfo(processDefinitionKey, latest.getVersion(), activeInstanceCount));
    }

    @Override
    public BpmnDeploymentResult deploy(String tenantId, String filename, byte[] bpmnXml) {
        Deployment deployment = repositoryService
                .createDeployment()
                .name(filename)
                .tenantId(tenantId)
                .addBytes(bpmnResourceName(filename), bpmnXml)
                .deploy();
        ProcessDefinition definition = repositoryService
                .createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        return new BpmnDeploymentResult(definition.getKey(), definition.getVersion(), deployment.getId());
    }

    private static String bpmnResourceName(String filename) {
        for (String suffix : BPMN_RESOURCE_SUFFIXES) {
            if (filename.endsWith(suffix)) {
                return filename;
            }
        }
        return filename + ".bpmn20.xml";
    }
}
