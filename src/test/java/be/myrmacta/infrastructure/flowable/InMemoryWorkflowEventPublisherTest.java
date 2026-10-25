package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.workflow.model.WorkflowCompletedEvent;
import be.myrmacta.domain.workflow.model.WorkflowFailedEvent;
import be.myrmacta.domain.workflow.model.WorkflowStartedEvent;
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
    void publish_acceptsStartedCompletedAndFailed() {
        Instant at = Instant.parse("2026-08-02T12:00:00Z");
        UUID id = UUID.randomUUID();
        publisher.publish(new WorkflowStartedEvent(id, at, "smokeProcess"));
        publisher.publish(new WorkflowCompletedEvent(id, at));
        publisher.publish(new WorkflowFailedEvent(id, at, "boom"));

        assertThat(publisher.published()).hasSize(3);
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
