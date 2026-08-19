package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.infrastructure.config.PurgeProperties;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class PurgeOutboxJob {

    private static final Logger log = LoggerFactory.getLogger(PurgeOutboxJob.class);

    private final ProcessPurgeOutboxUseCase processPurgeOutboxUseCase;
    private final PurgeProperties purgeProperties;

    PurgeOutboxJob(ProcessPurgeOutboxUseCase processPurgeOutboxUseCase, PurgeProperties purgeProperties) {
        this.processPurgeOutboxUseCase =
                Objects.requireNonNull(processPurgeOutboxUseCase, "processPurgeOutboxUseCase");
        this.purgeProperties = Objects.requireNonNull(purgeProperties, "purgeProperties");
    }

    @Scheduled(fixedDelayString = "${app.purge.poll-delay:1h}")
    void process() {
        int processed = processPurgeOutboxUseCase.processBatch(Instant.now(), purgeProperties.batchSize());
        if (processed > 0) {
            log.info("Purge outbox job processed {} row(s)", processed);
        }
    }
}
