package com.flowforge.infrastructure.connector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.connector.model.ConnectorInstanceData;
import com.flowforge.domain.connector.port.in.ConnectorTriggerRuntime;
import com.flowforge.domain.connector.port.out.ConnectorInstanceRepository;
import com.flowforge.domain.connector.port.out.ConnectorScheduleStateRepository;
import com.flowforge.infrastructure.config.InboundConnectorProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §87 (D3c, D3d) — cadence propre à chaque instance, créneau pris avant l'interrogation, instances isolées. */
class InboundPollingConnectorTriggerJobTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private ConnectorInstanceRepository instances;
    private ConnectorScheduleStateRepository state;
    private ConnectorTriggerRuntime runtime;
    private InboundPollingConnectorTriggerJob job;

    @BeforeEach
    void setUp() {
        instances = mock(ConnectorInstanceRepository.class);
        state = mock(ConnectorScheduleStateRepository.class);
        when(state.claimTrigger(any(), any(), any())).thenReturn(true);
        runtime = mock(ConnectorTriggerRuntime.class);
        job = new InboundPollingConnectorTriggerJob(instances, state, runtime, new TenantProperties(TENANT), InboundConnectorProperties.defaults());
    }

    private static ConnectorInstanceData instance(String type, boolean enabled, Object pollSeconds) {
        Map<String, Object> config = pollSeconds == null ? Map.of() : Map.of("pollSeconds", pollSeconds);
        return new ConnectorInstanceData(UUID.randomUUID(), TENANT, "n", type, enabled, config);
    }

    @Test
    void aNeverPolledInstanceIsPolledNow_andOnlyTheInboundTypesAreConsidered() {
        var imap = instance("imap-inbound", true, 60);
        var bus = instance("azure-service-bus", true, null);
        var other = instance("kafka", true, 60);
        when(instances.listByTenantId(TENANT)).thenReturn(List.of(imap, bus, other));
        when(state.findLastTriggeredAt(any())).thenReturn(Optional.empty());

        job.process(NOW);

        verify(runtime).onIncomingEvent(imap, Map.of());
        verify(runtime).onIncomingEvent(bus, Map.of());
        verify(runtime, never()).onIncomingEvent(eq(other), any());
    }

    @Test
    void eachInstanceFollowsItsOwnIntervalWithAFloor() {
        var due = instance("imap-inbound", true, 60);
        var notYet = instance("imap-inbound", true, 600);
        var tooFast = instance("imap-inbound", true, 5); // plancher 30 s : 20 s écoulées, pas encore dû
        when(instances.listByTenantId(TENANT)).thenReturn(List.of(due, notYet, tooFast));
        when(state.findLastTriggeredAt(due.id())).thenReturn(Optional.of(NOW.minusSeconds(61)));
        when(state.findLastTriggeredAt(notYet.id())).thenReturn(Optional.of(NOW.minusSeconds(61)));
        when(state.findLastTriggeredAt(tooFast.id())).thenReturn(Optional.of(NOW.minusSeconds(20)));

        job.process(NOW);

        verify(runtime).onIncomingEvent(due, Map.of());
        verify(runtime, never()).onIncomingEvent(eq(notYet), any());
        verify(runtime, never()).onIncomingEvent(eq(tooFast), any());
    }

    @Test
    void aDisabledInstanceIsIgnored_andALostClaimMeansAnotherRunnerHasIt() {
        var disabled = instance("imap-inbound", false, 60);
        var contended = instance("imap-inbound", true, 60);
        when(instances.listByTenantId(TENANT)).thenReturn(List.of(disabled, contended));
        when(state.findLastTriggeredAt(any())).thenReturn(Optional.empty());
        when(state.claimTrigger(eq(contended.id()), any(), any())).thenReturn(false);

        job.process(NOW);

        verify(runtime, never()).onIncomingEvent(any(), any());
    }

    @Test
    void aFailingInstanceKeepsItsSlot_noHammeringEveryTick_andDoesNotStopTheOthers() {
        var broken = instance("imap-inbound", true, 60);
        var healthy = instance("azure-service-bus", true, 60);
        when(instances.listByTenantId(TENANT)).thenReturn(List.of(broken, healthy));
        when(state.findLastTriggeredAt(any())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("mailbox access failed")).when(runtime).onIncomingEvent(eq(broken), any());

        job.process(NOW);

        // Revue D3c/D3d (M4) : le créneau n'est pas rendu, la prochaine tentative attend l'intervalle de l'instance.
        verify(state, never()).releaseTrigger(any(), any(), any());
        verify(runtime).onIncomingEvent(healthy, Map.of());
    }
}
