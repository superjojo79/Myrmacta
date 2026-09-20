package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.connector")
public record ConnectorProperties(
        Crypto crypto,
        Webhook webhook,
        Http http,
        DocumentStorage documentStorage,
        BatchImport batchImport,
        Kafka kafka) {

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
        if (batchImport == null) {
            batchImport = new BatchImport(10_485_760L, 50_000);
        }
        if (kafka == null) {
            kafka = new Kafka(Duration.ofSeconds(5), 10);
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

    /**
     * @param maxFileBytes bound on a single downloaded file, same DoS-prevention pattern as
     *     {@link Http#maxResponseBytes()} (D-BATCH-SIZE-LIMIT, connecteurs-cible) — a file reported
     *     larger than this at listing time is rejected before any download attempt, and the actual
     *     download itself is also bounded (never buffers more than {@code maxFileBytes + 1} bytes),
     *     so a server misreporting a smaller size at listing time cannot force an unbounded read.
     * @param maxRows bound on the number of data rows parsed from one file, same rationale.
     */
    public record BatchImport(long maxFileBytes, int maxRows) {
        public BatchImport {
            if (maxFileBytes <= 0) {
                maxFileBytes = 10_485_760L;
            }
            if (maxRows <= 0) {
                maxRows = 50_000;
            }
        }
    }

    /**
     * @param maxInstancesPerTenant enabled {@code kafka} instances a tenant may have at once —
     *     each one runs a real consumer thread/connection for the JVM's lifetime, unlike a
     *     cron-polled connector, so leaving this unbounded lets a config mistake or a compromised
     *     admin account exhaust resources shared by the whole tenant (found in adversarial review,
     *     connecteurs-cible lot B1).
     */
    public record Kafka(Duration consumerPollTimeout, int maxInstancesPerTenant) {
        public Kafka {
            if (consumerPollTimeout == null) {
                consumerPollTimeout = Duration.ofSeconds(5);
            }
            if (maxInstancesPerTenant <= 0) {
                maxInstancesPerTenant = 10;
            }
        }
    }
}
