package com.flowforge.infrastructure.flowable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.workflow.model.OutboxEvent;
import com.flowforge.domain.workflow.model.TriggerOutboxPayload;
import com.flowforge.domain.workflow.port.out.OutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.config.WorkflowProperties;
import com.flowforge.infrastructure.observability.MdcScope;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class OutboxRelaySchedulerTest {

    private OutboxRepository outboxRepository;
    private AttachWorkflowUseCase attachWorkflow;
    private WorkflowEnginePort workflowEnginePort;
    private OutboxRelayScheduler scheduler;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        WorkflowProperties properties = new WorkflowProperties(
                Duration.ofMinutes(5),
                new WorkflowProperties.Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7)),
                Duration.ofDays(7));
        scheduler = new OutboxRelayScheduler(
                outboxRepository,
                attachWorkflow,
                workflowEnginePort,
                properties,
                new com.flowforge.infrastructure.config.TenantProperties("tenant_dev"));
    }

    @Test
    void processOne_happyPathMarksProcessedAfterStartAndConfirm() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(
                        event.payload().dossierId(),
                        event.payload().processKey(),
                        event.payload().triggerRef(),
                        OutboxRelayScheduler.SYSTEM_OUTBOX_STARTED_BY))
                .thenReturn(reserved);
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(reserved.id().toString()))
                .thenReturn(Optional.empty());
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap())).thenReturn("proc-1");

        scheduler.processOne(event);

        verify(workflowEnginePort)
                .start(eq("smokeProcess"), eq("tenant_dev"), eq(reserved.id().toString()), anyMap());
        verify(attachWorkflow).confirmStarted(reserved.id(), "proc-1");
        verify(outboxRepository).markProcessed(event.eventId());
    }

    @Test
    void processOne_confirmsExistingInstanceByBusinessKeyWithoutSecondStart() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any())).thenReturn(reserved);
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(reserved.id().toString()))
                .thenReturn(Optional.of("proc-existing"));

        scheduler.processOne(event);

        verify(attachWorkflow).confirmStarted(reserved.id(), "proc-existing");
        verify(outboxRepository).markProcessed(event.eventId());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void processOne_rejectsReservedVariablesBeforeReserve() {
        OutboxEvent event = OutboxEvent.pending(
                new TriggerOutboxPayload(
                        UUID.randomUUID(),
                        null,
                        "smokeProcess",
                        Map.of("dossierId", "client-forbidden"),
                        "seed:bad-vars"),
                Instant.parse("2026-08-02T10:00:00Z"));

        scheduler.processOne(event);

        verify(attachWorkflow, never()).reserveOrResume(any(), any(), any(), any());
        verify(outboxRepository).markDead(eq(event.eventId()), anyString());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void processOne_recordsFailureOnTransientEngineError() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any())).thenReturn(reserved);
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(reserved.id().toString()))
                .thenReturn(Optional.empty());
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("temporary"));

        scheduler.processOne(event);

        verify(outboxRepository).recordFailure(event.eventId(), "temporary", 5);
        verify(outboxRepository, never()).markProcessed(any());
        verify(outboxRepository, never()).markDead(any(), any());
    }

    @Test
    void processOne_marksDeadImmediatelyOnActiveWorkflowCollision() {
        OutboxEvent event = pendingEvent();
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any()))
                .thenThrow(new WorkflowAlreadyRunningException(event.payload().dossierId()));

        scheduler.processOne(event);

        verify(outboxRepository).markDead(eq(event.eventId()), anyString());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void processOne_marksProcessedWithoutSecondStartWhenLinkAlreadyActive() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink active = reservedLink(event.payload().dossierId()).withProcessInstance("proc-1");
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any())).thenReturn(active);

        scheduler.processOne(event);

        verify(outboxRepository).markProcessed(event.eventId());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
        verify(attachWorkflow, never()).confirmStarted(any(), any());
    }

    @Test
    void processOne_putsDossierIdThenClearsMdc() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink active = reservedLink(event.payload().dossierId()).withProcessInstance("proc-1");
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any())).thenAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.TENANT_ID)).isEqualTo("tenant_dev");
            org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.DOSSIER_ID))
                    .isEqualTo(event.payload().dossierId().toString());
            org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isNull();
            return active;
        });

        scheduler.processOne(event);

        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.DOSSIER_ID)).isNull();
        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.TENANT_ID)).isNull();
        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isNull();
    }

    @Test
    void processOne_putsProcessInstanceIdAfterStartThenClears() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(any(), any(), any(), any())).thenReturn(reserved);
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(reserved.id().toString()))
                .thenReturn(Optional.empty());
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap())).thenReturn("proc-1");
        doAnswer(invocation -> {
            org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isEqualTo("proc-1");
            org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.DOSSIER_ID))
                    .isEqualTo(event.payload().dossierId().toString());
            return null;
        })
                .when(attachWorkflow)
                .confirmStarted(reserved.id(), "proc-1");

        scheduler.processOne(event);

        org.assertj.core.api.Assertions.assertThat(MDC.get(MdcScope.PROCESS_INSTANCE_ID)).isNull();
        verify(outboxRepository).markProcessed(event.eventId());
    }

    private static OutboxEvent pendingEvent() {
        return OutboxEvent.pending(
                new TriggerOutboxPayload(
                        UUID.randomUUID(), null, "smokeProcess", Map.of("k", "v"), "seed:test"),
                Instant.parse("2026-08-02T10:00:00Z"));
    }

    private static DossierWorkflowLink reservedLink(UUID dossierId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                "tenant_dev",
                null,
                "smokeProcess",
                1,
                "STARTED",
                "seed:test",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                OutboxRelayScheduler.SYSTEM_OUTBOX_STARTED_BY);
    }
}
