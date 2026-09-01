package com.flowforge.infrastructure.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SchedulerConnectorTriggerJobTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:30Z");

    private ConnectorInstanceRepository connectorInstanceRepository;
    private ConnectorScheduleStateRepository scheduleStateRepository;
    private ConnectorTriggerRuntime connectorTriggerRuntime;
    private SchedulerConnectorTriggerJob job;

    @BeforeEach
    void setUp() {
        connectorInstanceRepository = mock(ConnectorInstanceRepository.class);
        scheduleStateRepository = mock(ConnectorScheduleStateRepository.class);
        connectorTriggerRuntime = mock(ConnectorTriggerRuntime.class);
        job = new SchedulerConnectorTriggerJob(
                connectorInstanceRepository,
                scheduleStateRepository,
                connectorTriggerRuntime,
                new TenantProperties(TENANT));
    }

    @Test
    void process_instanceDueEveryMinute_triggersAndUpdatesState() {
        ConnectorInstanceData instance = schedulerInstance("0 * * * * *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(instance, Map.of());
        verify(scheduleStateRepository).upsert(instance.id(), NOW);
    }

    @Test
    void process_instanceNotYetDue_neverTriggers() {
        ConnectorInstanceData instance = schedulerInstance("0 0 0 1 1 *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));

        job.process(NOW);

        verify(connectorTriggerRuntime, never()).onIncomingEvent(any(), any());
        verify(scheduleStateRepository, never()).upsert(eq(instance.id()), any());
    }

    @Test
    void process_neverTriggeredBefore_isTreatedAsDue() {
        ConnectorInstanceData instance = schedulerInstance("0 0 0 1 1 *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id())).thenReturn(Optional.empty());

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(instance, Map.of());
        verify(scheduleStateRepository).upsert(instance.id(), NOW);
    }

    @Test
    void process_twoInstancesOneDueOneNot_onlyDueOneTriggers() {
        ConnectorInstanceData due = schedulerInstance("0 * * * * *");
        ConnectorInstanceData notDue = schedulerInstance("0 0 0 1 1 *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(due, notDue));
        when(scheduleStateRepository.findLastTriggeredAt(any())).thenReturn(Optional.empty());
        when(scheduleStateRepository.findLastTriggeredAt(notDue.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(due, Map.of());
        verify(connectorTriggerRuntime, never()).onIncomingEvent(notDue, Map.of());
    }

    @Test
    void process_recoversStaleProcessingBeforePollNotApplicable_scheduledMethodDelegatesToProcess() {
        // process(Instant) is the unit under test above; @Scheduled poll() only supplies Instant.now(),
        // exercised here just to prove it delegates without throwing.
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of());

        job.poll();

        verify(connectorInstanceRepository).listByTenantId(TENANT);
    }

    private static ConnectorInstanceData schedulerInstance(String cronExpression) {
        return new ConnectorInstanceData(
                UUID.randomUUID(),
                TENANT,
                "Scheduler smoke",
                "scheduler",
                true,
                Map.of(
                        "cronExpression", cronExpression,
                        "processKey", "smokeProcess",
                        "dossierTypeCode", "CLAIM"));
    }
}
