package com.flowforge.infrastructure.flowable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxRelaySchedulerTest {

    private OutboxRepository outboxRepository;
    private AttachWorkflowUseCase attachWorkflow;
    private WorkflowEnginePort workflowEnginePort;
    private OutboxRelayScheduler scheduler;

    @BeforeEach
    void setUp() {
        outboxRepository = mock(OutboxRepository.class);
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        WorkflowProperties properties = new WorkflowProperties(
                Duration.ofMinutes(5),
                new WorkflowProperties.Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7)));
        scheduler = new OutboxRelayScheduler(outboxRepository, attachWorkflow, workflowEnginePort, properties);
    }

    @Test
    void processOne_happyPathMarksProcessedAfterStartAndConfirm() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(
                        event.payload().dossierId(),
                        event.payload().processKey(),
                        event.payload().triggerRef()))
                .thenReturn(reserved);
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap())).thenReturn("proc-1");

        scheduler.processOne(event);

        verify(workflowEnginePort)
                .start(eq("smokeProcess"), eq("tenant_dev"), eq(reserved.id().toString()), anyMap());
        verify(attachWorkflow).confirmStarted(reserved.id(), "proc-1");
        verify(outboxRepository).markProcessed(event.eventId());
    }

    @Test
    void processOne_recordsFailureOnTransientEngineError() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink reserved = reservedLink(event.payload().dossierId());
        when(attachWorkflow.reserveOrResume(any(), any(), any())).thenReturn(reserved);
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
        when(attachWorkflow.reserveOrResume(any(), any(), any()))
                .thenThrow(new WorkflowAlreadyRunningException(event.payload().dossierId()));

        scheduler.processOne(event);

        verify(outboxRepository).markDead(eq(event.eventId()), anyString());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void processOne_marksProcessedWithoutSecondStartWhenLinkAlreadyActive() {
        OutboxEvent event = pendingEvent();
        DossierWorkflowLink active = reservedLink(event.payload().dossierId()).withProcessInstance("proc-1");
        when(attachWorkflow.reserveOrResume(any(), any(), any())).thenReturn(active);

        scheduler.processOne(event);

        verify(outboxRepository).markProcessed(event.eventId());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
        verify(attachWorkflow, never()).confirmStarted(any(), any());
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
                null);
    }
}
