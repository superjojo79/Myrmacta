package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.connector")
public record ConnectorProperties(Crypto crypto, Webhook webhook, Http http, DocumentStorage documentStorage) {

    public ConnectorProperties {
        if (crypto == null) {
            crypto = new Crypto(null);
        }
        if (webhook == null) {
            webhook = new Webhook(1_048_576L, 120, Duration.ofHours(24));
        }
        if (http == null) {
            http = new Http(Duration.ofSeconds(5), Duration.ofSeconds(5), 5_242_880L);
        }
        if (documentStorage == null) {
            documentStorage = new DocumentStorage(5_242_880L);
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

    public record Http(Duration connectTimeout, Duration requestTimeout, long maxResponseBytes) {
        public Http {
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(5);
            }
            if (requestTimeout == null) {
                requestTimeout = Duration.ofSeconds(5);
            }
            if (maxResponseBytes <= 0) {
                maxResponseBytes = 5_242_880L;
            }
        }
    }

    public record DocumentStorage(long maxContentSize) {
        public DocumentStorage {
            if (maxContentSize <= 0) {
                maxContentSize = 5_242_880L;
            }
        }
    }
}
