package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkflowStateEventListenerTest {

    private static final String TENANT = "tenant_dev";

    private DossierWorkflowLinkRepository linkRepository;
    private WorkflowParticipantPort participantPort;
    private SendNotificationUseCase sendNotificationUseCase;
    private WorkflowStateEventListener listener;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        participantPort = mock(WorkflowParticipantPort.class);
        sendNotificationUseCase = mock(SendNotificationUseCase.class);
        listener = new WorkflowStateEventListener(
                new TenantProperties(TENANT), linkRepository, participantPort, sendNotificationUseCase);
    }

    @Test
    void onEvent_fansOutBrowserWorkflowStateChangedToParticipants() {
        DossierWorkflowLink link = link("proc-1");
        when(linkRepository.findByProcessInstanceId(TENANT, "proc-1")).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub", "observer-sub"));

        listener.onEvent(new ProcessEvent("TASK_ASSIGNED", "proc-1"));

        ArgumentCaptor<NotificationEvent> events = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotificationUseCase, times(2)).send(events.capture(), eq(TENANT));
        assertThat(events.getAllValues())
                .extracting(NotificationEvent::userId)
                .containsExactly("starter-sub", "observer-sub");
        assertThat(events.getAllValues().getFirst().channels()).containsExactly(NotificationChannel.BROWSER);
        assertThat(events.getAllValues().getFirst().payload())
                .containsEntry("type", WorkflowStateEventListener.WORKFLOW_STATE_CHANGED)
                .containsEntry("linkId", link.id().toString())
                .doesNotContainKey("processInstanceId");
    }

    @Test
    void onEvent_ignoresUntrackedEventTypes() {
        listener.onEvent(new ProcessEvent("TASK_CREATED", "proc-1"));

        verifyNoInteractions(linkRepository, participantPort, sendNotificationUseCase);
    }

    @Test
    void onEvent_isFailSoftWhenNotificationSendFails() {
        DossierWorkflowLink link = link("proc-1");
        when(linkRepository.findByProcessInstanceId(TENANT, "proc-1")).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(sendNotificationUseCase.send(org.mockito.ArgumentMatchers.any(), eq(TENANT)))
                .thenThrow(new IllegalStateException("outbox down"));

        assertThatCode(() -> listener.onEvent(new ProcessEvent("PROCESS_COMPLETED", "proc-1")))
                .doesNotThrowAnyException();
        assertThat(listener.isFailOnException()).isFalse();
    }

    @Test
    void onEvent_noopsWhenLinkIsUnknown() {
        when(linkRepository.findByProcessInstanceId(TENANT, "proc-1")).thenReturn(Optional.empty());

        listener.onEvent(new ProcessEvent("ACTIVITY_COMPLETED", "proc-1"));

        verify(participantPort, never()).findParticipantUserIds(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(sendNotificationUseCase);
    }

    private static DossierWorkflowLink link(String processInstanceId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TENANT,
                processInstanceId,
                "smokeProcess",
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                "starter-sub");
    }

    private static final class ProcessEvent implements FlowableEvent {
        private final FlowableEventType type;
        private final String processInstanceId;

        private ProcessEvent(String typeName, String processInstanceId) {
            this.type = new NamedEventType(typeName);
            this.processInstanceId = processInstanceId;
        }

        @Override
        public FlowableEventType getType() {
            return type;
        }

        public String getProcessInstanceId() {
            return processInstanceId;
        }
    }

    private static final class NamedEventType implements FlowableEventType {
        private final String name;

        private NamedEventType(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
