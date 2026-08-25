package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.config.WorkflowProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DEV-PURGE (follow-up ops `workflow-engine`) : {@code OutboxRepository.purgeProcessedOlderThanDays}
 * et {@code IdempotencyStore.purgeExpired} existaient mais n'étaient appelés par aucun job.
 */
@Component
class WorkflowRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRetentionJob.class);

    private final OutboxRepository outboxRepository;
    private final IdempotencyStore idempotencyStore;
    private final WorkflowProperties properties;
    private final TenantProperties tenantProperties;

    WorkflowRetentionJob(
            OutboxRepository outboxRepository,
            IdempotencyStore idempotencyStore,
            WorkflowProperties properties,
            TenantProperties tenantProperties) {
        this.outboxRepository = Objects.requireNonNull(outboxRepository, "outboxRepository");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Scheduled(fixedDelayString = "${app.workflow.retention.poll-delay:1d}")
    void purge() {
        try (MdcScope ignored = MdcScope.tenant(tenantProperties.id())) {
            int purgedOutbox = outboxRepository.purgeProcessedOlderThanDays(
                    (int) properties.outbox().processedRetention().toDays());
            int purgedIdempotency = idempotencyStore.purgeExpired();
            if (purgedOutbox > 0 || purgedIdempotency > 0) {
                log.info(
                        "Workflow retention job purged {} outbox event(s), {} idempotency key(s)",
                        purgedOutbox,
                        purgedIdempotency);
            }
        }
    }
}
