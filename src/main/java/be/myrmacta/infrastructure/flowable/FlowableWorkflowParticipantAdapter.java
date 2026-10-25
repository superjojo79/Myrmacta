package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowParticipantAdapter implements WorkflowParticipantPort {

    private final HistoryService historyService;
    private final TaskService taskService;
    private final WorkflowTypeConfigRepository configRepository;

    FlowableWorkflowParticipantAdapter(
            HistoryService historyService,
            TaskService taskService,
            WorkflowTypeConfigRepository configRepository) {
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
    }

    @Override
    public List<String> findParticipantUserIds(DossierWorkflowLink link) {
        Set<String> participants = new LinkedHashSet<>();
        add(participants, link.startedBy());

        if (link.processInstanceId() != null && !link.processInstanceId().isBlank()) {
            var historicProcess = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(link.processInstanceId())
                    .processInstanceTenantId(link.tenantId())
                    .singleResult();
            if (historicProcess != null) {
                add(participants, historicProcess.getStartUserId());
            }
            taskService.createTaskQuery()
                    .processInstanceId(link.processInstanceId())
                    .taskTenantId(link.tenantId())
                    .list()
                    .stream()
                    .map(Task::getAssignee)
                    .forEach(assignee -> add(participants, assignee));
            historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(link.processInstanceId())
                    .taskTenantId(link.tenantId())
                    .list()
                    .stream()
                    .map(HistoricTaskInstance::getAssignee)
                    .forEach(assignee -> add(participants, assignee));
        }

        configRepository.findByWorkflowKey(link.tenantId(), link.processDefinitionKey())
                .ifPresent(config -> config.observerUserIds()
                        .forEach(observer -> add(participants, observer)));

        return List.copyOf(participants);
    }

    private static void add(Set<String> participants, String userId) {
        if (userId != null && !userId.isBlank()) {
            participants.add(userId);
        }
    }
}
