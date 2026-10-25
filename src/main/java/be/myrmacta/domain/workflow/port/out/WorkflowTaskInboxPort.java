package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import java.time.Instant;
import java.util.Objects;

public interface WorkflowTaskInboxPort {

    PageResult<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, PageQuery query);

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
            Objects.requireNonNull(assigneeId, "assigneeId");
            Objects.requireNonNull(processInstanceId, "processInstanceId");
        }

        /** Convenience for callers built before {@code formKey} existed (TODO §52). */
        public ActiveTaskSnapshot(
                String taskId, String name, String assigneeId, Instant createdAt, String processInstanceId) {
            this(taskId, name, assigneeId, createdAt, processInstanceId, null);
        }
    }
}
