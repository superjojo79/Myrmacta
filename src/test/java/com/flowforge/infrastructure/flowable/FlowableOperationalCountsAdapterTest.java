package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.flowable.engine.ManagementService;
import org.flowable.engine.TaskService;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;

class FlowableOperationalCountsAdapterTest {

    @Test
    void current_countsDeadLetterJobsAndUnassignedActiveTasks() {
        ManagementService managementService = mock(ManagementService.class);
        TaskService taskService = mock(TaskService.class);
        DeadLetterJobQuery deadLetter = mock(DeadLetterJobQuery.class);
        TaskQuery tasks = mock(TaskQuery.class);
        when(managementService.createDeadLetterJobQuery()).thenReturn(deadLetter);
        when(deadLetter.count()).thenReturn(2L);
        when(taskService.createTaskQuery()).thenReturn(tasks);
        when(tasks.taskTenantId("tenant_dev")).thenReturn(tasks);
        when(tasks.taskUnassigned()).thenReturn(tasks);
        when(tasks.active()).thenReturn(tasks);
        when(tasks.count()).thenReturn(5L);

        var counts = new FlowableOperationalCountsAdapter(managementService, taskService).current("tenant_dev");

        assertThat(counts.deadLetterJobCount()).isEqualTo(2);
        assertThat(counts.unassignedOpenTaskCount()).isEqualTo(5);
    }

    @Test
    void current_returnsZeroWhenFlowableFails() {
        ManagementService managementService = mock(ManagementService.class);
        TaskService taskService = mock(TaskService.class);
        when(managementService.createDeadLetterJobQuery()).thenThrow(new RuntimeException("down"));
        when(taskService.createTaskQuery()).thenThrow(new RuntimeException("down"));

        var counts = new FlowableOperationalCountsAdapter(managementService, taskService).current("tenant_dev");

        assertThat(counts.deadLetterJobCount()).isZero();
        assertThat(counts.unassignedOpenTaskCount()).isZero();
    }
}
