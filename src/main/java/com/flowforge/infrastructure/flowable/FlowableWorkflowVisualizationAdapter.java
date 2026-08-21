package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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

    FlowableWorkflowVisualizationAdapter(
            RepositoryService repositoryService,
            RuntimeService runtimeService,
            HistoryService historyService,
            TaskService taskService) {
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.historyService = Objects.requireNonNull(historyService, "historyService");
        this.taskService = Objects.requireNonNull(taskService, "taskService");
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
        List<String> completedIds = activities.stream()
                .filter(activity -> activity.getEndTime() != null)
                .map(HistoricActivityInstance::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<String> failedIds = activities.stream()
                .filter(activity -> activity.getDeleteReason() != null
                        && activity.getDeleteReason().startsWith("error"))
                .map(HistoricActivityInstance::getActivityId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return new WorkflowDiagramView(
                processModelXml(process.processDefinitionId()),
                activeIds,
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
                .listPage(query.offset(), query.size())
                .stream()
                .map(FlowableWorkflowVisualizationAdapter::toTaskView)
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

    private static WorkflowTaskView toTaskView(Task task) {
        return new WorkflowTaskView(
                task.getId(),
                task.getName(),
                task.getAssignee(),
                task.getTaskDefinitionKey(),
                toInstant(task.getCreateTime()));
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }

    private record ProcessReference(String processDefinitionId, boolean active) {}
}
