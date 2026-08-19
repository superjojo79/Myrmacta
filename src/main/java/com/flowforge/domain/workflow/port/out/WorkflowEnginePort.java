package com.flowforge.domain.workflow.port.out;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowEnginePort {

    String start(String processDefinitionKey, String tenantId, String businessKey, Map<String, Object> variables);

    void completeTask(String taskId, Map<String, Object> variables);

    Optional<String> findProcessInstanceIdByTaskId(String taskId);

    Optional<String> findProcessInstanceIdByBusinessKey(String businessKey);

    Optional<String> findDossierIdVariable(String processInstanceId);

    /**
     * Best-effort runtime process-instance delete. Missing PI is a no-op. Failures must not
     * propagate (ACT_HI residual is acceptable).
     */
    void deleteProcessInstanceBestEffort(String processInstanceId);
}
