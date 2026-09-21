package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.reporting.model.OperationalCounts;
import com.flowforge.domain.reporting.port.out.OperationalCountsPort;
import java.util.Objects;
import org.flowable.engine.ManagementService;
import org.flowable.engine.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Dead-letter jobs are counted on the Flowable schema of this instance, the same query as
 * {@link FlowableJobMetricsBinder}. One process hosts one tenant, so that schema is the tenant
 * boundary; {@code DeadLetterJobQuery} is not filtered again. Open tasks are restricted to the
 * tenant id, unassigned, and active (completed tasks live in history and are absent here).
 */
@Component
public class FlowableOperationalCountsAdapter implements OperationalCountsPort {

    private static final Logger log = LoggerFactory.getLogger(FlowableOperationalCountsAdapter.class);

    private final ManagementService managementService;
    private final TaskService taskService;

    public FlowableOperationalCountsAdapter(ManagementService managementService, TaskService taskService) {
        this.managementService = Objects.requireNonNull(managementService, "managementService");
        this.taskService = Objects.requireNonNull(taskService, "taskService");
    }

    @Override
    public OperationalCounts current(String tenantId) {
        return new OperationalCounts(deadLetterCount(), unassignedOpenTaskCount(tenantId));
    }

    private long deadLetterCount() {
        try {
            return managementService.createDeadLetterJobQuery().count();
        } catch (RuntimeException ex) {
            log.warn("Failed to collect Flowable dead-letter job count", ex);
            return 0L;
        }
    }

    private long unassignedOpenTaskCount(String tenantId) {
        try {
            return taskService
                    .createTaskQuery()
                    .taskTenantId(tenantId)
                    .taskUnassigned()
                    .active()
                    .count();
        } catch (RuntimeException ex) {
            log.warn("Failed to collect unassigned open task count", ex);
            return 0L;
        }
    }
}
