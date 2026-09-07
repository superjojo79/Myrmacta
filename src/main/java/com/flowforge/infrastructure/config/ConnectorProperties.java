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
            webhook = new Webhook(1_048_576L, 120, Duration.ofHours(24), 0);
        }
        if (http == null) {
            http = new Http(Duration.ofSeconds(5), Duration.ofSeconds(5), 5_242_880L);
        }
        if (documentStorage == null) {
            documentStorage = new DocumentStorage(5_242_880L);
        }
    }

    public record Crypto(String masterKey) {}

    /**
     * @param preAuthRateLimitPerMinute per-instance ceiling applied <em>before</em> webhook
     *     authentication (TODO §57 revue adversariale PR #113). {@code rateLimitPerMinute} moved
     *     after {@code authenticate()} in the PR (Q10) specifically so garbage/unsigned traffic
     *     can no longer exhaust a legitimate integration's budget and lock it out — but that left
     *     unauthenticated requests (HMAC/parsing cost, bandwidth) completely unbounded. This is a
     *     coarser, separate ceiling — always kept at least {@code rateLimitPerMinute * 10} (self-
     *     healing below that floor, so a low misconfiguration can't turn it back into the
     *     legitimate-traffic bottleneck Q10 removed) — bounding abuse without reintroducing the
     *     targeted-lockout risk.
     */
    public record Webhook(
            long maxBodyBytes, int rateLimitPerMinute, Duration idempotencyTtl, int preAuthRateLimitPerMinute) {
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
            if (preAuthRateLimitPerMinute < rateLimitPerMinute) {
                preAuthRateLimitPerMinute = rateLimitPerMinute * 10;
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
