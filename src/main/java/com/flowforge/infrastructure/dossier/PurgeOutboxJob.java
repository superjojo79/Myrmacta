package com.flowforge.infrastructure.dossier;

import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.infrastructure.config.PurgeProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.observability.MdcScope;
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
    private final TenantProperties tenantProperties;

    PurgeOutboxJob(
            ProcessPurgeOutboxUseCase processPurgeOutboxUseCase,
            PurgeProperties purgeProperties,
            TenantProperties tenantProperties) {
        this.processPurgeOutboxUseCase =
                Objects.requireNonNull(processPurgeOutboxUseCase, "processPurgeOutboxUseCase");
        this.purgeProperties = Objects.requireNonNull(purgeProperties, "purgeProperties");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Scheduled(fixedDelayString = "${app.purge.poll-delay:1h}")
    void process() {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            Instant now = Instant.now();
            int recovered = processPurgeOutboxUseCase.recoverStaleProcessing(
                    now.minus(purgeProperties.processingTimeout()));
            if (recovered > 0) {
                log.warn("Purge outbox job recovered {} row(s) stuck in PROCESSING", recovered);
            }
            int processed = processPurgeOutboxUseCase.processBatch(now, purgeProperties.batchSize());
            if (processed > 0) {
                log.info("Purge outbox job processed {} row(s)", processed);
            }
        }
    }
}
