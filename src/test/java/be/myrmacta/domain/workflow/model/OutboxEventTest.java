package be.myrmacta.domain.workflow.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void pending_createsPendingEventWithPayload() {
        var payload = new TriggerOutboxPayload(
                UUID.randomUUID(), null, "smokeProcess", Map.of("k", "v"), "seed:test");

        OutboxEvent event = OutboxEvent.pending(payload, Instant.parse("2026-08-02T10:00:00Z"));

        assertThat(event.status()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.attempts()).isZero();
        assertThat(event.payload().processKey()).isEqualTo("smokeProcess");
        assertThat(event.payload().variables()).containsEntry("k", "v");
        assertThat(event.eventId()).isNotNull();
    }
}
