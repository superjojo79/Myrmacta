package be.myrmacta.infrastructure.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.connector.port.out.ConnectorScheduleStateRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BatchImportConnectorTriggerJobTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-09-01T12:00:30Z");

    private ConnectorInstanceRepository connectorInstanceRepository;
    private ConnectorScheduleStateRepository scheduleStateRepository;
    private ConnectorTriggerRuntime connectorTriggerRuntime;
    private BatchImportConnectorTriggerJob job;

    @BeforeEach
    void setUp() {
        connectorInstanceRepository = mock(ConnectorInstanceRepository.class);
        scheduleStateRepository = mock(ConnectorScheduleStateRepository.class);
        when(scheduleStateRepository.claimTrigger(any(), any(), any())).thenReturn(true);
        connectorTriggerRuntime = mock(ConnectorTriggerRuntime.class);
        job = new BatchImportConnectorTriggerJob(
                connectorInstanceRepository,
                scheduleStateRepository,
                connectorTriggerRuntime,
                new TenantProperties(TENANT));
    }

    @Test
    void process_instanceDue_triggersAndUpdatesState() {
        ConnectorInstanceData instance = batchImportInstance("0 * * * * *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(instance, Map.of());
        verify(scheduleStateRepository).claimTrigger(eq(instance.id()), any(), eq(NOW));
    }

    @Test
    void process_instanceNotYetDue_neverTriggers() {
        ConnectorInstanceData instance = batchImportInstance("0 0 0 1 1 *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));

        job.process(NOW);

        verify(connectorTriggerRuntime, never()).onIncomingEvent(any(), any());
        verify(scheduleStateRepository, never()).claimTrigger(eq(instance.id()), any(), any());
    }

    @Test
    void process_disabledInstance_isIgnored() {
        ConnectorInstanceData instance = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "Batch", "batch-import", false,
                Map.of("cronExpression", "0 * * * * *", "processKey", "p", "dossierTypeCode", "T"));
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));

        job.process(NOW);

        verify(connectorTriggerRuntime, never()).onIncomingEvent(any(), any());
    }

    @Test
    void process_otherConnectorTypes_areIgnored() {
        ConnectorInstanceData schedulerInstance = new ConnectorInstanceData(
                UUID.randomUUID(), TENANT, "Scheduler", "scheduler", true,
                Map.of("cronExpression", "0 * * * * *", "processKey", "p", "dossierTypeCode", "T"));
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(schedulerInstance));

        job.process(NOW);

        verify(connectorTriggerRuntime, never()).onIncomingEvent(any(), any());
    }

    @Test
    void process_neverTriggeredBefore_isTreatedAsDue() {
        ConnectorInstanceData instance = batchImportInstance("0 0 0 1 1 *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id())).thenReturn(Optional.empty());

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(instance, Map.of());
        verify(scheduleStateRepository).claimTrigger(eq(instance.id()), any(), eq(NOW));
    }

    @Test
    void process_reusesSharedScheduleStateRepository_sameAsSchedulerConnector() {
        // D-BATCH-CRON-JOB (design) : nouveau job dédié, mais table de dernier déclenchement
        // réutilisée telle quelle (déjà générique, indexée par connectorInstanceId).
        ConnectorInstanceData instance = batchImportInstance("0 * * * * *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id())).thenReturn(Optional.empty());

        job.process(NOW);

        verify(scheduleStateRepository).claimTrigger(eq(instance.id()), any(), eq(NOW));
    }

    @Test
    void process_oneInstanceThrows_othersStillProcessed() {
        // T-BIJ-ONE-INSTANCE-FAILS : divergence délibérée par rapport à SchedulerConnectorTriggerJob
        // (qui n'isole pas ses instances) — voir doc implementation.
        ConnectorInstanceData failing = batchImportInstance("0 * * * * *");
        ConnectorInstanceData healthy = batchImportInstance("0 * * * * *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(failing, healthy));
        when(scheduleStateRepository.findLastTriggeredAt(any())).thenReturn(Optional.empty());
        doThrow(new RuntimeException("sftp down")).when(connectorTriggerRuntime).onIncomingEvent(failing, Map.of());

        job.process(NOW);

        verify(connectorTriggerRuntime).onIncomingEvent(healthy, Map.of());
        verify(scheduleStateRepository).claimTrigger(eq(healthy.id()), any(), eq(NOW));
        verify(scheduleStateRepository).releaseTrigger(eq(failing.id()), any(), eq(NOW));
    }

    @Test
    void poll_delegatesToProcess() {
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of());

        job.poll();

        verify(connectorInstanceRepository).listByTenantId(TENANT);
    }

    private static ConnectorInstanceData batchImportInstance(String cronExpression) {
        return new ConnectorInstanceData(
                UUID.randomUUID(),
                TENANT,
                "Batch import smoke",
                "batch-import",
                true,
                Map.of(
                        "cronExpression", cronExpression,
                        "processKey", "smokeProcess",
                        "dossierTypeCode", "CLAIM"));
    }

    // ---- TODO §79 (D5) : le créneau est pris avant le déclenchement ---------------------------------------------------

    @Test
    void process_slotClaimedByAnotherExecutor_doesNotTrigger() {
        ConnectorInstanceData instance = batchImportInstance("0 * * * * *");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id()))
                .thenReturn(Optional.of(Instant.parse("2026-09-01T11:59:00Z")));
        when(scheduleStateRepository.claimTrigger(any(), any(), any())).thenReturn(false);

        job.process(NOW);

        verify(connectorTriggerRuntime, never()).onIncomingEvent(any(), any());
        verify(scheduleStateRepository, never()).releaseTrigger(any(), any(), any());
    }

    @Test
    void process_claimsWithTheObservedLastTriggerThenReleasesItWhenTheTriggerFails() {
        ConnectorInstanceData instance = batchImportInstance("0 * * * * *");
        Instant last = Instant.parse("2026-09-01T11:59:00Z");
        when(connectorInstanceRepository.listByTenantId(TENANT)).thenReturn(List.of(instance));
        when(scheduleStateRepository.findLastTriggeredAt(instance.id())).thenReturn(Optional.of(last));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(connectorTriggerRuntime).onIncomingEvent(any(), any());

        job.process(NOW);

        var order = org.mockito.Mockito.inOrder(scheduleStateRepository, connectorTriggerRuntime);
        order.verify(scheduleStateRepository).claimTrigger(instance.id(), last, NOW);
        order.verify(connectorTriggerRuntime).onIncomingEvent(any(), any());
        order.verify(scheduleStateRepository).releaseTrigger(instance.id(), last, NOW);
    }
}
