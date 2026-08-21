package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import java.time.Instant;
import java.util.Objects;

public interface WorkflowTaskInboxPort {

    PageResult<ActiveTaskSnapshot> findActiveAssignedTo(String assigneeId, PageQuery query);

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
