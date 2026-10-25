package be.myrmacta.infrastructure.flowable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableSignalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableSignalEventListenerTest {

    private RuntimeService runtimeService;
    private ConnectorInstanceRepository repository;
    private ConnectorTriggerRuntime triggerRuntime;
    private FlowableSignalEventListener listener;

    @BeforeEach
    void setUp() {
        runtimeService = mock(RuntimeService.class);
        repository = mock(ConnectorInstanceRepository.class);
        triggerRuntime = mock(ConnectorTriggerRuntime.class);
        listener = new FlowableSignalEventListener(
                runtimeService, repository, triggerRuntime, new TenantProperties("tenant_dev"));
    }

    @Test
    void activitySignaled_invokesMatchingEnabledInstances() {
        UUID matchId = UUID.randomUUID();
        ConnectorInstanceData match = new ConnectorInstanceData(
                matchId,
                "tenant_dev",
                "Match",
                "flowable-signal",
                true,
                Map.of("signalName", "orderCreated", "processKey", "p", "dossierTypeCode", "T"));
        ConnectorInstanceData disabled = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Disabled",
                "flowable-signal",
                false,
                Map.of("signalName", "orderCreated", "processKey", "p", "dossierTypeCode", "T"));
        ConnectorInstanceData otherSignal = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Other",
                "flowable-signal",
                true,
                Map.of("signalName", "other", "processKey", "p", "dossierTypeCode", "T"));
        ConnectorInstanceData http = new ConnectorInstanceData(
                UUID.randomUUID(),
                "tenant_dev",
                "Http",
                "http-rest",
                true,
                Map.of("signalName", "orderCreated"));
        when(repository.listByTenantId("tenant_dev"))
                .thenReturn(List.of(match, disabled, otherSignal, http));

        FlowableSignalEvent event = mock(FlowableSignalEvent.class);
        when(event.getSignalName()).thenReturn("orderCreated");
        when(event.getExecutionId()).thenReturn("exec-1");
        when(event.getProcessInstanceId()).thenReturn("pi-1");
        when(event.getActivityId()).thenReturn("waitSignal");
        when(event.getSignalData()).thenReturn(Map.of("ref", "R-1"));

        listener.activitySignaled(event);

        verify(triggerRuntime).onIncomingEvent(eq(match), any());
        verify(triggerRuntime, never()).onIncomingEvent(eq(disabled), any());
        verify(triggerRuntime, never()).onIncomingEvent(eq(otherSignal), any());
        verify(triggerRuntime, never()).onIncomingEvent(eq(http), any());
    }

    @Test
    void activitySignaled_blankSignalName_ignored() {
        FlowableSignalEvent event = mock(FlowableSignalEvent.class);
        when(event.getSignalName()).thenReturn("  ");
        listener.activitySignaled(event);
        verify(repository, never()).listByTenantId(any());
        verify(triggerRuntime, never()).onIncomingEvent(any(), any());
    }
}
