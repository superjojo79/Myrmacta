package com.flowforge.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.purge")
public record PurgeProperties(Duration pollDelay, int batchSize, Duration processingTimeout) {

    public PurgeProperties {
        if (pollDelay == null) {
            pollDelay = Duration.ofHours(1);
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (processingTimeout == null) {
            processingTimeout = Duration.ofHours(2);
        }
    }
}
