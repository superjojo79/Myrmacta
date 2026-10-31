package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.connector.model.CallbackAmbiguousCorrelationException;
import be.myrmacta.domain.connector.model.CallbackNoMatchingInstanceException;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowEngineAdapter implements WorkflowEnginePort {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;

    FlowableWorkflowEngineAdapter(RuntimeService runtimeService, TaskService taskService) {
        this(runtimeService, taskService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    FlowableWorkflowEngineAdapter(RuntimeService runtimeService, TaskService taskService, HistoryService historyService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
        this.historyService = historyService;
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
        Optional<String> running = Optional.ofNullable(runtimeService.createProcessInstanceQuery()
                        .processInstanceBusinessKey(businessKey)
                        .singleResult())
                .map(processInstance -> processInstance.getProcessInstanceId());
        if (running.isPresent() || historyService == null) {
            return running;
        }
        // TODO §79 (D5, revue) : un process court a pu TERMINER entre son démarrage et la confirmation du lien ; le chercher aussi
        // dans l'historique évite que la réconciliation le relance une seconde fois.
        return historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByProcessInstanceStartTime().asc()
                .listPage(0, 1).stream()
                .findFirst()
                .map(historic -> historic.getId());
    }

    @Override
    public Optional<String> findDossierIdVariable(String processInstanceId) {
        Object value = runtimeService.getVariable(processInstanceId, "dossierId");
        return Optional.ofNullable(value).map(Object::toString);
    }

    @Override
    public Optional<String> findFormKeyByTaskId(String taskId) {
        return Optional.ofNullable(taskService.createTaskQuery().taskId(taskId).singleResult())
                .map(org.flowable.task.api.Task::getFormKey);
    }

    @Override
    public Optional<String> findAssigneeByTaskId(String taskId) {
        return Optional.ofNullable(taskService.createTaskQuery().taskId(taskId).singleResult())
                .map(org.flowable.task.api.Task::getAssignee)
                .filter(assignee -> assignee != null && !assignee.isBlank());
    }

    @Override
    public void deleteProcessInstanceBestEffort(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return;
        }
        try {
            runtimeService.deleteProcessInstance(processInstanceId, "purge");
        } catch (RuntimeException ex) {
            if (isMissingProcessInstance(ex)) {
                return;
            }
            org.slf4j.LoggerFactory.getLogger(FlowableWorkflowEngineAdapter.class)
                    .warn("Best-effort process instance delete failed for {}: {}", processInstanceId, ex.toString());
        }
    }

    @Override
    public void correlateMessage(
            String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables) {
        correlate(messageName, correlationKey, correlationValue, variables, false);
    }

    @Override
    public void correlateMessageOnExecution(
            String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables) {
        correlate(messageName, correlationKey, correlationValue, variables, true);
    }

    @Override
    public int correlateMessageToAllWaiting(String messageName, String dossierId, Map<String, Object> variables) {
        int resumed = 0;
        for (Execution execution : runtimeService.createExecutionQuery().messageEventSubscriptionName(messageName)
                .processVariableValueEquals(WorkflowVariables.DOSSIER_ID, dossierId).list()) {
            try {
                runtimeService.messageEventReceived(messageName, execution.getId(), variables);
                resumed++;
            } catch (FlowableException alreadyResumed) {
                // Même course que D-CALLBACK-RACE : l'exécution a repris entre la requête et l'envoi ; ce n'est pas une erreur.
            }
        }
        return resumed;
    }

    private void correlate(
            String messageName, String correlationKey, Object correlationValue, Map<String, Object> variables,
            boolean executionLocal) {
        var query = runtimeService.createExecutionQuery().messageEventSubscriptionName(messageName);
        List<Execution> matches = (executionLocal
                        ? query.variableValueEquals(correlationKey, correlationValue)
                        : query.processVariableValueEquals(correlationKey, correlationValue))
                .list();
        if (matches.isEmpty()) {
            throw new CallbackNoMatchingInstanceException(messageName, correlationKey, correlationValue);
        }
        if (matches.size() > 1) {
            throw new CallbackAmbiguousCorrelationException(
                    messageName, correlationKey, correlationValue, matches.size());
        }
        try {
            runtimeService.messageEventReceived(messageName, matches.get(0).getId(), variables);
        } catch (FlowableException ex) {
            // D-CALLBACK-RACE (design NFR) : the execution matched by list() above may have been
            // resumed by a concurrent callback in the tiny window before this call -- Flowable
            // throws FlowableException "if the execution has not subscribed to the signal"
            // (RuntimeService#messageEventReceived javadoc). Treated the same as no match at all,
            // never a raw 500.
            throw new CallbackNoMatchingInstanceException(messageName, correlationKey, correlationValue);
        }
    }

    private static boolean isMissingProcessInstance(RuntimeException ex) {
        String name = ex.getClass().getSimpleName();
        if (name.contains("ObjectNotFound") || name.contains("NotFound")) {
            return true;
        }
        String message = ex.getMessage();
        return message != null && message.toLowerCase().contains("does not exist");
    }
}
