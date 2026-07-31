package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.StartWorkflowCommand;
import com.flowforge.domain.workflow.model.WorkflowInstance;
import com.flowforge.domain.workflow.model.WorkflowStartedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryWorkflowEventPublisherTest {

    private InMemoryWorkflowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new InMemoryWorkflowEventPublisher();
    }

    @Test
    void publish_recordsEvent() {
        WorkflowInstance instance = WorkflowInstance.create(
                new StartWorkflowCommand("demo-process", "tenant-a"));
        var event = new WorkflowStartedEvent(instance);

        publisher.publish(event);

        assertThat(publisher.published()).containsExactly(event);
    }

    @Test
    void publish_preservesOrder() {
        WorkflowInstance first = WorkflowInstance.create(
                new StartWorkflowCommand("p1", "tenant-a"));
        WorkflowInstance second = WorkflowInstance.create(
                new StartWorkflowCommand("p2", "tenant-a"));

        publisher.publish(new WorkflowStartedEvent(first));
        publisher.publish(new WorkflowStartedEvent(second));

        assertThat(publisher.published())
                .extracting(e -> e.instance().processKey())
                .containsExactly("p1", "p2");
    }
}
