package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WorkflowInstanceTest {

    @Test
    void create_producesRunningInstanceWithId() {
        var cmd = new StartWorkflowCommand("demo-process", "tenant-a");

        WorkflowInstance instance = WorkflowInstance.create(cmd);

        assertThat(instance.id()).isNotNull();
        assertThat(instance.processKey()).isEqualTo("demo-process");
        assertThat(instance.tenantId()).isEqualTo("tenant-a");
        assertThat(instance.status()).isEqualTo(WorkflowStatus.RUNNING);
    }

    @Test
    void complete_fromRunning_returnsCompleted() {
        WorkflowInstance running = WorkflowInstance.create(
                new StartWorkflowCommand("demo-process", "tenant-a"));

        WorkflowInstance completed = running.complete();

        assertThat(completed.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(completed.id()).isEqualTo(running.id());
    }

    @Test
    void complete_whenNotRunning_throwsInvalidState() {
        WorkflowInstance completed = WorkflowInstance.create(
                        new StartWorkflowCommand("demo-process", "tenant-a"))
                .complete();

        assertThatThrownBy(completed::complete)
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("Workflow non actif");
    }

    @Test
    void create_rejectsNullCommand() {
        assertThatThrownBy(() -> WorkflowInstance.create(null))
                .isInstanceOf(NullPointerException.class);
    }
}
