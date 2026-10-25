package be.myrmacta.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowEventTest {

    @Test
    void sealedHierarchyCoversStartedCompletedFailed() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.parse("2026-08-02T12:00:00Z");

        WorkflowEvent started = new WorkflowStartedEvent(id, at, "smokeProcess");
        WorkflowEvent completed = new WorkflowCompletedEvent(id, at);
        WorkflowEvent failed = new WorkflowFailedEvent(id, at, "boom");

        assertThat(describe(started)).isEqualTo("started");
        assertThat(describe(completed)).isEqualTo("completed");
        assertThat(describe(failed)).isEqualTo("failed");
    }

    private static String describe(WorkflowEvent event) {
        return switch (event) {
            case WorkflowStartedEvent e -> "started";
            case WorkflowCompletedEvent e -> "completed";
            case WorkflowFailedEvent e -> "failed";
        };
    }
}
