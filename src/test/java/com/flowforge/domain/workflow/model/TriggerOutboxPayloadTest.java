package com.flowforge.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TriggerOutboxPayloadTest {

    @Test
    void rejectsBlankProcessKey() {
        assertThatThrownBy(() -> new TriggerOutboxPayload(
                        UUID.randomUUID(), UUID.randomUUID(), "   ", Map.of(), "trigger:smoke"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processKey");
    }
}
