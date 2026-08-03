package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.workflow")
public record WorkflowProperties(Duration reserveTimeout, Outbox outbox) {

    public WorkflowProperties {
        if (reserveTimeout == null) {
            reserveTimeout = Duration.ofMinutes(5);
        }
        if (outbox == null) {
            outbox = new Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7));
        }
    }

    public record Outbox(int batchSize, int maxAttempts, Duration pollDelay, Duration processedRetention) {
        public Outbox {
            if (batchSize <= 0) {
                batchSize = 20;
            }
            if (maxAttempts <= 0) {
                maxAttempts = 5;
            }
            if (pollDelay == null) {
                pollDelay = Duration.ofSeconds(1);
            }
            if (processedRetention == null) {
                processedRetention = Duration.ofDays(7);
            }
        }
    }
}
