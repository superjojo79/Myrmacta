package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Component;

@Component
class FlowableWorkflowTaskInboxAdapter implements WorkflowTaskInboxPort {

    private final TaskService taskService;

    FlowableWorkflowTaskInboxAdapter(TaskService taskService) {
        this.taskService = taskService;
    }

    @Override
    public PageResult<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, PageQuery query) {
        return page(() -> taskService.createTaskQuery().taskAssignee(assigneeId).active(), query);
    }

    /**
     * TODO §113 (D-GROUP-ASSIGN) — assignée à l'utilisateur, OU candidate pour l'un de ses groupes. Une requête « candidat » de
     * Flowable ne rend que les tâches sans assigné : une tâche prise par un collègue sort de la liste.
     */
    @Override
    public PageResult<ActiveTaskSnapshot> findActiveForUser(String userId, Set<String> groups, PageQuery query) {
        if (groups == null || groups.isEmpty()) {
            return findActiveAssignedTo(userId, query);
        }
        List<String> candidateGroups = groups.stream().sorted().toList();
        return page(() -> taskService.createTaskQuery()
                .or()
                .taskAssignee(userId)
                .taskCandidateGroupIn(candidateGroups)
                .endOr()
                .active(), query);
    }

    private PageResult<ActiveTaskSnapshot> page(Supplier<TaskQuery> filter, PageQuery query) {
        long total = filter.get().count();
        var tasks = filter.get()
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
