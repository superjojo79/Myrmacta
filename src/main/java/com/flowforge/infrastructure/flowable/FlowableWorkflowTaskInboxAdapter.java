package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Instant;
import java.util.Date;
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
    public PageResult<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, PageQuery query) {
        long total = taskService.createTaskQuery().taskAssignee(assigneeId).active().count();
        var tasks = taskService.createTaskQuery()
                .taskAssignee(assigneeId)
                .active()
                .orderByTaskCreateTime()
                .desc()
                .listPage(query.offset(), query.size());
        var content = tasks.stream()
                .map(task -> new ActiveTaskSnapshot(
                        task.getId(),
                        task.getName() == null ? "" : task.getName(),
                        task.getAssignee(),
                        toInstant(task.getCreateTime()),
                        task.getProcessInstanceId(),
                        task.getFormKey()))
                .toList();
        return PageResult.of(content, query.page(), query.size(), total);
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
