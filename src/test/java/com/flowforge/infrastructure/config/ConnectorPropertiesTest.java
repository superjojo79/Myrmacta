package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorPropertiesTest {

    @Test
    void defaults_matchDesignHardening() {
        var props = new ConnectorProperties(null, null);

        assertThat(props.crypto().masterKey()).isNull();
        assertThat(props.webhook().maxBodyBytes()).isEqualTo(1_048_576L);
        assertThat(props.webhook().rateLimitPerMinute()).isEqualTo(120);
        assertThat(props.webhook().idempotencyTtl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void customValues_arePreserved() {
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto("dGVzdC1rZXktMzItYnl0ZXMtbG9uZyEh"),
                new ConnectorProperties.Webhook(256_000L, 60, Duration.ofHours(6)));

        assertThat(props.crypto().masterKey()).isEqualTo("dGVzdC1rZXktMzItYnl0ZXMtbG9uZyEh");
        assertThat(props.webhook().maxBodyBytes()).isEqualTo(256_000L);
        assertThat(props.webhook().rateLimitPerMinute()).isEqualTo(60);
        assertThat(props.webhook().idempotencyTtl()).isEqualTo(Duration.ofHours(6));
    }
}
