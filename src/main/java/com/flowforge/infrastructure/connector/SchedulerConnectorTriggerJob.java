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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Decides when to fire a {@code scheduler}-type connector instance — {@code SchedulerConnector}
 * itself has no knowledge of timing, it only turns config into a {@code WorkflowTriggerEvent} once
 * asked. A 3rd caller of {@link ConnectorTriggerRuntime#onIncomingEvent} from infra, alongside
 * {@code WebhookController} (http-rest) and {@code FlowableSignalEventListener} (flowable-signal).
 */
@Component
class SchedulerConnectorTriggerJob {

    private static final String SCHEDULER_TYPE = "scheduler";

    private final ConnectorInstanceRepository connectorInstanceRepository;
    private final ConnectorScheduleStateRepository scheduleStateRepository;
    private final ConnectorTriggerRuntime connectorTriggerRuntime;
    private final TenantProperties tenantProperties;

    SchedulerConnectorTriggerJob(
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

    @Scheduled(fixedDelayString = "${app.connector.scheduler.poll-delay:1m}")
    void poll() {
        process(Instant.now());
    }

    void process(Instant now) {
        for (ConnectorInstanceData instance : connectorInstanceRepository.listByTenantId(tenantProperties.id())) {
            if (!SCHEDULER_TYPE.equals(instance.connectorType()) || !instance.enabled()) {
                continue;
            }
            if (isDue(instance, now)) {
                connectorTriggerRuntime.onIncomingEvent(instance, Map.of());
                scheduleStateRepository.upsert(instance.id(), now);
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
