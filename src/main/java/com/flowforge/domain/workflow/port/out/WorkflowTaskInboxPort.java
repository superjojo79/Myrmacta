package com.flowforge.domain.workflow.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public interface WorkflowTaskInboxPort {

    List<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, int limit);

    record ActiveTaskSnapshot(
            String taskId,
            String name,
            String assigneeId,
            Instant createdAt,
            String processInstanceId) {

        public ActiveTaskSnapshot {
            Objects.requireNonNull(taskId, "taskId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(assigneeId, "assigneeId");
            Objects.requireNonNull(processInstanceId, "processInstanceId");
        }
    }
}
