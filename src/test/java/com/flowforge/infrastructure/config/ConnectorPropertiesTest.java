package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConnectorPropertiesTest {

    @Test
    void defaults_matchDesignHardening() {
        var props = new ConnectorProperties(null, null, null, null);

        assertThat(props.crypto().masterKey()).isNull();
        assertThat(props.webhook().maxBodyBytes()).isEqualTo(1_048_576L);
        assertThat(props.webhook().rateLimitPerMinute()).isEqualTo(120);
        assertThat(props.webhook().idempotencyTtl()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void documentStorageDefaults_matchWebhookMaxBodyConvention() {
        var props = new ConnectorProperties(null, null, null, null);

        assertThat(props.documentStorage().maxContentSize()).isEqualTo(5_242_880L);
    }

    @Test
    void documentStorageCustomValue_isPreserved() {
        var props = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(1_024L));

        assertThat(props.documentStorage().maxContentSize()).isEqualTo(1_024L);
    }

    @Test
    void documentStorageNonPositiveValue_fallsBackToDefault() {
        var props = new ConnectorProperties(
                null, null, null, new ConnectorProperties.DocumentStorage(0L));

        assertThat(props.documentStorage().maxContentSize()).isEqualTo(5_242_880L);
    }

    @Test
    void customValues_arePreserved() {
        var props = new ConnectorProperties(
                new ConnectorProperties.Crypto("dGVzdC1rZXktMzItYnl0ZXMtbG9uZyEh"),
                new ConnectorProperties.Webhook(256_000L, 60, Duration.ofHours(6)),
                new ConnectorProperties.Http(Duration.ofSeconds(2), Duration.ofSeconds(3)),
                null);

        assertThat(props.crypto().masterKey()).isEqualTo("dGVzdC1rZXktMzItYnl0ZXMtbG9uZyEh");
        assertThat(props.webhook().maxBodyBytes()).isEqualTo(256_000L);
        assertThat(props.webhook().rateLimitPerMinute()).isEqualTo(60);
        assertThat(props.webhook().idempotencyTtl()).isEqualTo(Duration.ofHours(6));
        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(props.http().requestTimeout()).isEqualTo(Duration.ofSeconds(3));
    }

    @Test
    void httpDefaults_matchNotificationWebhookConvention() {
        var props = new ConnectorProperties(null, null, null, null);

        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(props.http().requestTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void httpPartialOverride_fillsMissingFieldWithDefault() {
        var props = new ConnectorProperties(
                null, null, new ConnectorProperties.Http(Duration.ofSeconds(1), null), null);

        assertThat(props.http().connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(props.http().requestTimeout()).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void httpPartialOverride_fillsMissingConnectTimeoutWithDefault() {
        var http = new ConnectorProperties.Http(null, Duration.ofSeconds(9));

        assertThat(http.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(http.requestTimeout()).isEqualTo(Duration.ofSeconds(9));
    }
}
