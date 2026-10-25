package be.myrmacta.infrastructure.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.retention")
public record RetentionProperties(Duration pollDelay, int batchSize) {

    public RetentionProperties {
        if (pollDelay == null) {
            pollDelay = Duration.ofHours(1);
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
    }
}
