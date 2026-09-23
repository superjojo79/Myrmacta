package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(Outbox outbox, Webhook webhook, Sse sse, Inbox inbox) {

    public NotificationProperties {
        if (outbox == null) {
            outbox = new Outbox(20, 3, Duration.ofSeconds(1), Duration.ofDays(7));
        }
        if (webhook == null) {
            webhook = new Webhook(Duration.ofSeconds(5), Duration.ofSeconds(5));
        }
        if (sse == null) {
            sse = new Sse(Duration.ofSeconds(15));
        }
        if (inbox == null) {
            inbox = new Inbox(Duration.ofDays(30), Duration.ofDays(1));
        }
    }

    public record Outbox(int batchSize, int maxAttempts, Duration pollDelay, Duration retention) {
        public Outbox {
            if (batchSize <= 0) {
                batchSize = 20;
            }
            if (maxAttempts <= 0) {
                maxAttempts = 3;
            }
            if (pollDelay == null) {
                pollDelay = Duration.ofSeconds(1);
            }
            if (retention == null) {
                retention = Duration.ofDays(7);
            }
        }
    }

    public record Webhook(Duration connectTimeout, Duration readTimeout) {
        public Webhook {
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(5);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(5);
            }
        }
    }

    public record Sse(Duration heartbeatInterval) {
        public Sse {
            if (heartbeatInterval == null) {
                heartbeatInterval = Duration.ofSeconds(15);
            }
        }
    }

    /** D-NOTIF-RETENTION (TODO §66): {@code user_notification} rétention, distincte de {@link Outbox#retention()}. */
    public record Inbox(Duration retention, Duration pollDelay) {
        public Inbox {
            if (retention == null) {
                retention = Duration.ofDays(30);
            }
            if (pollDelay == null) {
                pollDelay = Duration.ofDays(1);
            }
        }
    }
}
