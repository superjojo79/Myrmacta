package be.myrmacta.infrastructure.flowable;

import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.identitylink.api.IdentityLink;
import be.myrmacta.domain.workflow.model.TaskGroupReassignment;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.ActivityHistoryEntry;
import be.myrmacta.domain.workflow.model.WorkflowDiagramView;
import be.myrmacta.domain.workflow.model.WorkflowIncident;
import be.myrmacta.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import be.myrmacta.domain.workflow.model.WorkflowTaskAlreadyAssignedException;
import be.myrmacta.domain.workflow.model.WorkflowTaskNotFoundException;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import be.myrmacta.domain.workflow.port.out.WorkflowIncidentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowVisualizationAdapter implements WorkflowVisualizationPort {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final TaskService taskService;
    private final WorkflowIncidentPort incidentPort;

    FlowableWorkflowVisualizationAdapter(
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            HistoryService historyService,
            TaskService taskService,
            WorkflowIncidentPort incidentPort) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.incidentPort = Objects.requireNonNull(incidentPort, "incidentPort");
    }

    @Override
    public WorkflowDiagramView getDiagram(String processInstanceId, String tenantId) {
        ProcessReference process = requireProcess(processInstanceId, tenantId);
        List<HistoricActivityInstance> activities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityTenantId(tenantId)
                .list();
        List<String> activeIds = process.active()
                ? runtimeService.getActiveActivityIds(processInstanceId)
                : List.of();
        // TODO §94 (D-INCIDENT-DIAGRAM) : une étape dont le job est en échec (tentatives restantes ou dead letter) est
        // marquée en erreur — et reste active : l'exécution l'attend toujours.
        List<String> incidentIds = process.active()
                ? incidentPort.listByProcessInstance(processInstanceId, tenantId).stream()
                        .map(WorkflowIncident::activityId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
                : List.of();
        List<String> completedIds = activities.stream()
                .filter(activity -> activity.getEndTime() != null)
                .map(HistoricActivityInstance::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> failedIds = java.util.stream.Stream.concat(
                        activities.stream()
                                .filter(activity -> activity.getDeleteReason() != null
                                        && activity.getDeleteReason().startsWith("error"))
                                .map(HistoricActivityInstance::getActivityId),
                        incidentIds.stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new WorkflowDiagramView(
                processModelXml(process.processDefinitionId()),
                java.util.stream.Stream.concat(incidentIds.stream(), activeIds.stream()).distinct().toList(),
                completedIds,
                failedIds);
    }

    @Override
    public PageResult<ActivityHistoryEntry> getHistory(String processInstanceId, String tenantId, PageQuery query) {
        requireProcess(processInstanceId, tenantId);
        long total = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityTenantId(tenantId)
                .count();
        var content = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .activityTenantId(tenantId)
                .orderByHistoricActivityInstanceStartTime()
                .asc()
                .listPage(query.offset(), query.size())
                .stream()
                .map(FlowableWorkflowVisualizationAdapter::toHistoryEntry)
                .toList();
        return PageResult.of(content, query.page(), query.size(), total);
    }

    @Override
    public PageResult<WorkflowTaskView> listActiveTasks(String processInstanceId, String tenantId, PageQuery query) {
        requireProcess(processInstanceId, tenantId);
        long total = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskTenantId(tenantId)
                .count();
        var content = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskTenantId(tenantId)
                .orderByTaskCreateTime()
                .asc()
                .listPage(query.offset(), query.size())
                .stream()
                .map(task -> toTaskView(task, candidateGroups(task.getId())))
                .toList();
        return PageResult.of(content, query.page(), query.size(), total);
    }

    @Override
    public void assertTaskOnInstance(String processInstanceId, String taskId, String tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task == null
                || !processInstanceId.equals(task.getProcessInstanceId())
                || !tenantId.equals(task.getTenantId())) {
            throw new WorkflowTaskNotFoundException("Workflow task not found on instance: " + taskId);
        }
    }

    @Override
    public void reassignTask(String processInstanceId, String taskId, String assigneeId, String tenantId) {
        assertTaskOnInstance(processInstanceId, taskId, tenantId);
        taskService.setAssignee(taskId, assigneeId);
    }

    @Override
    public void claimTask(String processInstanceId, String taskId, String userId, String tenantId) {
        assertTaskOnInstance(processInstanceId, taskId, tenantId);
        try {
            // claim() (et non setAssignee) : Flowable refuse atomiquement une tâche déjà prise par
            // un autre, ce qui tranche aussi la course entre deux « M'assigner » simultanés.
            taskService.claim(taskId, userId);
        } catch (FlowableTaskAlreadyClaimedException ex) {
            throw new WorkflowTaskAlreadyAssignedException("Workflow task already assigned: " + taskId);
        }
    }

    @Override
    public TaskGroupReassignment reassignTaskToGroup(String processInstanceId, String taskId, String groupCode, String tenantId) {
        assertTaskOnInstance(processInstanceId, taskId, tenantId);
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        List<String> previousGroups = taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getGroupId() != null)
                .map(IdentityLink::getGroupId)
                .sorted()
                .toList();
        for (String previous : previousGroups) {
            taskService.deleteCandidateGroup(taskId, previous);
        }
        if (task.getAssignee() != null) {
            taskService.unclaim(taskId);
        }
        taskService.addCandidateGroup(taskId, groupCode);
        return new TaskGroupReassignment(task.getName(), previousGroups, task.getAssignee());
    }

    private ProcessReference requireProcess(String processInstanceId, String tenantId) {
        var runtime = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .processInstanceTenantId(tenantId)
                .singleResult();
        if (runtime != null) {
            return new ProcessReference(runtime.getProcessDefinitionId(), true);
        }
        var historic = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .processInstanceTenantId(tenantId)
                .singleResult();
        if (historic != null) {
            return new ProcessReference(historic.getProcessDefinitionId(), false);
        }
        throw new WorkflowProcessInstanceNotFoundException(
                "Workflow process instance not found: " + processInstanceId);
    }

    private String processModelXml(String processDefinitionId) {
        try (InputStream stream = repositoryService.getProcessModel(processDefinitionId)) {
            if (stream == null) {
                throw new WorkflowProcessInstanceNotFoundException(
                        "Workflow process definition model not found: " + processDefinitionId);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static ActivityHistoryEntry toHistoryEntry(HistoricActivityInstance activity) {
        return new ActivityHistoryEntry(
                activity.getActivityId(),
                activity.getActivityName(),
                activity.getAssignee(),
                toInstant(activity.getStartTime()),
                toInstant(activity.getEndTime()),
                activity.getDurationInMillis());
    }

    private static WorkflowTaskView toTaskView(Task task, List<String> candidateGroups) {
        return new WorkflowTaskView(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                task.getTaskDefinitionKey(),
                toInstant(task.getCreateTime()),
                task.getFormKey(),
                candidateGroups);
    }

    /** TODO §113 — groupes candidats d'une tâche (une page de tâches actives : quelques lectures). */
    private List<String> candidateGroups(String taskId) {
        return taskService.getIdentityLinksForTask(taskId).stream()
                .filter(link -> IdentityLinkType.CANDIDATE.equals(link.getType()) && link.getGroupId() != null)
                .map(IdentityLink::getGroupId)
                .sorted()
                .toList();
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private record ProcessReference(String processDefinitionId, boolean active) {}
}
