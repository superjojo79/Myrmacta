package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public interface WorkflowTaskInboxPort {

    PageResult<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, PageQuery query);

    /**
     * TODO §114 (D-GROUP-ASSIGN) — tâches actives assignées à l'utilisateur, ou libres et candidates pour l'un de ses
     * groupes ({@code assigneeId} nul). Sans groupe : comme {@link #findActiveAssignedTo}.
     */
    PageResult<ActiveTaskSnapshot> findActiveForUser(String userId, Set<String> groups, PageQuery query);

    record ActiveTaskSnapshot(
            String taskId,
            String name,
            String assigneeId,
            Instant createdAt,
            String processInstanceId,
            String formKey) {

        public ActiveTaskSnapshot {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(processInstanceId, "processInstanceId");
        }

        /** Convenience for callers built before {@code formKey} existed (TODO §52). */
        public ActiveTaskSnapshot(
                String taskId, String name, String assigneeId, Instant createdAt, String processInstanceId) {
            this(taskId, name, assigneeId, createdAt, processInstanceId, null);
        }
    }
}
