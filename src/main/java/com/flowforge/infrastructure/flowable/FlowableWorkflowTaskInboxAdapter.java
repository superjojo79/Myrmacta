package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowTaskInboxAdapter implements WorkflowTaskInboxPort {

    private final TaskService taskService;

    FlowableWorkflowTaskInboxAdapter(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public List<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, int limit) {
        List<Task> tasks = taskService.createTaskQuery()
                .taskAssignee(assigneeId)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .listPage(0, limit);
        return tasks.stream()
                .map(task -> new ActiveTaskSnapshot(
                        task.getId(),
                        task.getName() == null ? "" : task.getName(),
                        task.getAssignee(),
                        toInstant(task.getCreateTime()),
                        task.getProcessInstanceId()))
                .toList();
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
