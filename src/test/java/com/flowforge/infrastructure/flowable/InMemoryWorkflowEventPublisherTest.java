package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.WorkflowStartedEvent;
import java.time.Instant;
import java.util.UUID;
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
        var event = new WorkflowStartedEvent(UUID.randomUUID(), Instant.now(), "demo-process");

        publisher.publish(event);

        assertThat(publisher.published()).containsExactly(event);
    }

    @Test
    void publish_preservesOrder() {
        var first = new WorkflowStartedEvent(UUID.randomUUID(), Instant.now(), "p1");
        var second = new WorkflowStartedEvent(UUID.randomUUID(), Instant.now(), "p2");

        publisher.publish(first);
        publisher.publish(second);

        assertThat(publisher.published())
                .extracting(e -> ((WorkflowStartedEvent) e).processKey())
                .containsExactly("p1", "p2");
    }
}
