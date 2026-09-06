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
     * Server-side read of {@code flowable:formKey} for the given task — used by
     * {@code CompleteTaskUseCaseImpl} (kind {@code user-question}, TODO §55) to resolve which
     * {@code TaskQuestionSchema} to validate against without trusting a value submitted by the
     * client (D-USERQ-FORMKEY-PARSING; see {@code TaskQuestionFormKeys}).
     */
    Optional<String> findFormKeyByTaskId(String taskId);

    /**
     * Best-effort runtime process-instance delete. Missing PI is a no-op. Failures must not
     * propagate (ACT_HI residual is acceptable).
     */
    void deleteProcessInstanceBestEffort(String processInstanceId);

    /**
     * Resumes the single process execution subscribed to {@code messageName} whose process
     * variable {@code correlationKey} equals {@code correlationValue}, injecting {@code variables}
     * on resume.
     *
     * @throws com.flowforge.domain.connector.model.CallbackNoMatchingInstanceException
     *     if no execution matches (D-CALLBACK-RACE)
     * @throws com.flowforge.domain.connector.model.CallbackAmbiguousCorrelationException
     *     if more than one execution matches (D-CALLBACK-MULTI-MATCH) -- never resolved silently
     */
    void correlateMessage(
            String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables);
}
