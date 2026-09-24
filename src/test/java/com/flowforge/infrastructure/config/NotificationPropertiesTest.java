package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NotificationPropertiesTest {

    @Test
    void defaults_matchDesign() {
        var props = new NotificationProperties(null, null, null, null);

        assertThat(props.outbox().batchSize()).isEqualTo(20);
        assertThat(props.outbox().maxAttempts()).isEqualTo(3);
        assertThat(props.outbox().pollDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.outbox().retention()).isEqualTo(Duration.ofDays(7));
        assertThat(props.webhook().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.webhook().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.sse().heartbeatInterval()).isEqualTo(Duration.ofSeconds(15));
        assertThat(props.inbox().retention()).isEqualTo(Duration.ofDays(30));
        assertThat(props.inbox().pollDelay()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void customValues_arePreserved() {
        var props = new NotificationProperties(
                new NotificationProperties.Outbox(10, 5, Duration.ofMillis(500), Duration.ofDays(3)),
                new NotificationProperties.Webhook(Duration.ofSeconds(2), Duration.ofSeconds(8)),
                new NotificationProperties.Sse(Duration.ofSeconds(30)),
                new NotificationProperties.Inbox(Duration.ofDays(60), Duration.ofHours(12)));

        assertThat(props.outbox().batchSize()).isEqualTo(10);
        assertThat(props.outbox().maxAttempts()).isEqualTo(5);
        assertThat(props.outbox().pollDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(props.outbox().retention()).isEqualTo(Duration.ofDays(3));
        assertThat(props.webhook().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(props.webhook().readTimeout()).isEqualTo(Duration.ofSeconds(8));
        assertThat(props.sse().heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(props.inbox().retention()).isEqualTo(Duration.ofDays(60));
        assertThat(props.inbox().pollDelay()).isEqualTo(Duration.ofHours(12));
    }
}
