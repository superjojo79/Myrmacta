package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.connector")
public record ConnectorProperties(Crypto crypto, Webhook webhook) {

    public ConnectorProperties {
        if (crypto == null) {
            crypto = new Crypto(null);
        }
        if (webhook == null) {
            webhook = new Webhook(1_048_576L, 120, Duration.ofHours(24));
        }
    }

    public record Crypto(String masterKey) {}

    public record Webhook(long maxBodyBytes, int rateLimitPerMinute, Duration idempotencyTtl) {
        public Webhook {
            if (maxBodyBytes <= 0) {
                maxBodyBytes = 1_048_576L;
            }
            if (rateLimitPerMinute <= 0) {
                rateLimitPerMinute = 120;
            }
            if (idempotencyTtl == null) {
                idempotencyTtl = Duration.ofHours(24);
            }
        }
    }
}
