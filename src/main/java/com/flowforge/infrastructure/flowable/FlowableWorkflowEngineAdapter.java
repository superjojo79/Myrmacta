package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Map;
import java.util.Optional;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowEngineAdapter implements WorkflowEnginePort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    FlowableWorkflowEngineAdapter(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    public String start(String processDefinitionKey, String tenantId, String businessKey, Map<String, Object> variables) {
        return runtimeService
                .startProcessInstanceByKeyAndTenantId(processDefinitionKey, businessKey, variables, tenantId)
                .getProcessInstanceId();
    }

    @Override
    public void completeTask(String taskId, Map<String, Object> variables) {
        taskService.complete(taskId, variables);
    }

    @Override
    public Optional<String> findProcessInstanceIdByTaskId(String taskId) {
        return Optional.ofNullable(taskService.createTaskQuery().taskId(taskId).singleResult())
                .map(task -> task.getProcessInstanceId());
    }

    @Override
    public Optional<String> findProcessInstanceIdByBusinessKey(String businessKey) {
        return Optional.ofNullable(runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .singleResult())
                .map(processInstance -> processInstance.getProcessInstanceId());
    }

    @Override
    public Optional<String> findDossierIdVariable(String processInstanceId) {
        Object value = runtimeService.getVariable(processInstanceId, "dossierId");
        return Optional.ofNullable(value).map(Object::toString);
    }
}
