package com.flowforge.infrastructure.persistence.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowRepositoryTest {

    private InMemoryWorkflowRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryWorkflowRepository();
    }

    @Test
    void save_thenFindById_returnsInstance() {
        WorkflowInstance instance = WorkflowInstance.create(
                new StartWorkflowCommand("demo-process", "tenant_a"));

        repository.save(instance);

        assertThat(repository.findById(instance.id())).contains(instance);
    }

    @Test
    void findById_unknown_returnsEmpty() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void save_overwritesExistingId() {
        WorkflowInstance first = WorkflowInstance.create(
                new StartWorkflowCommand("demo-process", "tenant_a"));
        WorkflowInstance updated = first.complete();

        repository.save(first);
        repository.save(updated);

        assertThat(repository.findById(first.id())).contains(updated);
    }
}
