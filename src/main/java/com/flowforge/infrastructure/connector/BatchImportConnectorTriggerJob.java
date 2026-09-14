package com.flowforge.infrastructure.connector;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Decides when to fire a {@code batch-import}-type connector instance — same pattern as {@link
 * SchedulerConnectorTriggerJob} (poll, not event-driven, D-BATCH-CRON-JOB, design), kept as a
 * **separate dedicated class** rather than generalizing the existing job: zero risk of regression
 * on {@code scheduler}, already shipped. Reuses {@link ConnectorScheduleStateRepository} as-is —
 * that table is already keyed by {@code connectorInstanceId}, not the {@code scheduler} type.
 *
 * <p>Deliberately diverges from {@code SchedulerConnectorTriggerJob} on one point: each instance
 * is isolated in its own try/catch (T-BIJ-ONE-INSTANCE-FAILS, testing doc) so a single instance
 * failing (e.g. SFTP connection down) does not stop the rest of the same tenant's {@code
 * batch-import} instances from being polled in the same cycle — {@code SchedulerConnectorTriggerJob}
 * does not have this isolation today, left unchanged on purpose (D-BATCH-CRON-JOB).
 */
@Component
class BatchImportConnectorTriggerJob {

    private static final Logger log = LoggerFactory.getLogger(BatchImportConnectorTriggerJob.class);
    private static final String BATCH_IMPORT_TYPE = "batch-import";

    private final ConnectorInstanceRepository connectorInstanceRepository;
    private final ConnectorScheduleStateRepository scheduleStateRepository;
    private final ConnectorTriggerRuntime connectorTriggerRuntime;
    private final TenantProperties tenantProperties;

    BatchImportConnectorTriggerJob(
            ConnectorInstanceRepository connectorInstanceRepository,
            ConnectorScheduleStateRepository scheduleStateRepository,
            ConnectorTriggerRuntime connectorTriggerRuntime,
            TenantProperties tenantProperties) {
        this.connectorInstanceRepository =
                Objects.requireNonNull(connectorInstanceRepository, "connectorInstanceRepository");
        this.scheduleStateRepository = Objects.requireNonNull(scheduleStateRepository, "scheduleStateRepository");
        this.connectorTriggerRuntime = Objects.requireNonNull(connectorTriggerRuntime, "connectorTriggerRuntime");
        this.tenantProperties = Objects.requireNonNull(tenantProperties, "tenantProperties");
    }

    @Scheduled(fixedDelayString = "${app.connector.batch-import.poll-delay:1m}")
    void poll() {
        process(Instant.now());
    }

    void process(Instant now) {
        for (ConnectorInstanceData instance : connectorInstanceRepository.listByTenantId(tenantProperties.id())) {
            if (!BATCH_IMPORT_TYPE.equals(instance.connectorType()) || !instance.enabled()) {
                continue;
            }
            if (!isDue(instance, now)) {
                continue;
            }
            try {
                connectorTriggerRuntime.onIncomingEvent(instance, Map.of());
                scheduleStateRepository.upsert(instance.id(), now);
            } catch (RuntimeException failure) {
                log.warn(
                        "batch-import: instance {} failed this poll, will retry next cycle: {}",
                        instance.id(), failure.getMessage(), failure);
            }
        }
    }

    private boolean isDue(ConnectorInstanceData instance, Instant now) {
        Optional<Instant> lastTriggeredAt = scheduleStateRepository.findLastTriggeredAt(instance.id());
        if (lastTriggeredAt.isEmpty()) {
            return true;
        }
        Object cronExpressionValue = instance.config().get("cronExpression");
        if (!(cronExpressionValue instanceof String cronExpressionText)) {
            return false;
        }
        CronExpression cron = CronExpression.parse(cronExpressionText);
        ZonedDateTime lastFired = ZonedDateTime.ofInstant(lastTriggeredAt.get(), ZoneOffset.UTC);
        ZonedDateTime nextFireTime = cron.next(lastFired);
        return nextFireTime != null && !nextFireTime.isAfter(ZonedDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
