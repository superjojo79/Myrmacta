package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkflowStartedEventTest {

    @Test
    void rejectsBlankProcessKey() {
        assertThatThrownBy(() -> new WorkflowStartedEvent(UUID.randomUUID(), Instant.now(), "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processKey");
    }
}
