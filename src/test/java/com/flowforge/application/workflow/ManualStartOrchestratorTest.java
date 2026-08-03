package com.flowforge.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.UpdateDossierStatusUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.IdempotencyStore;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowEventPublisher;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualStartOrchestratorTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private DossierRepository dossierRepository;
    private DossierWorkflowLinkRepository linkRepository;
    private WorkflowTypeConfigRepository configRepository;
    private AttachWorkflowUseCase attachWorkflow;
    private WorkflowEnginePort workflowEnginePort;
    private IdempotencyStore idempotencyStore;
    private UpdateDossierStatusUseCase updateDossierStatusUseCase;
    private WorkflowEventPublisher eventPublisher;
    private ManualStartOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        idempotencyStore = mock(IdempotencyStore.class);
        updateDossierStatusUseCase = mock(UpdateDossierStatusUseCase.class);
        eventPublisher = mock(WorkflowEventPublisher.class);
        orchestrator = new ManualStartOrchestrator(
                dossierRepository,
                linkRepository,
                configRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                updateDossierStatusUseCase,
                eventPublisher);
    }

    @Test
    void start_reservesStartsConfirmsAndRemembersIdempotency() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString())).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap()))
                .thenReturn("proc-1");
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config(null)));
        when(linkRepository.findById(reserved.id()))
                .thenReturn(Optional.of(reserved.withProcessInstance("proc-1")));

        ManualStartOrchestrator.StartResult result =
                orchestrator.start(dossierId, PROCESS, Map.of("priority", "HIGH"), "idem-1");

        assertThat(result.replay()).isFalse();
        assertThat(result.link().isActive()).isTrue();
        verify(attachWorkflow).confirmStarted(reserved.id(), "proc-1");
        verify(idempotencyStore).remember(TENANT, dossierId, "idem-1", reserved.id());
        verify(workflowEnginePort).start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap());
    }

    @Test
    void start_replaysSameIdempotencyKeyWithoutSecondStart() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        DossierWorkflowLink existing = reservedLink(dossierId).withProcessInstance("proc-1");
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.of(existing.id()));
        when(linkRepository.findById(existing.id())).thenReturn(Optional.of(existing));

        ManualStartOrchestrator.StartResult result =
                orchestrator.start(dossierId, PROCESS, Map.of(), "idem-1");

        assertThat(result.replay()).isTrue();
        assertThat(result.link().id()).isEqualTo(existing.id());
        verify(attachWorkflow, never()).reserve(any(), any(), any());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void start_abortsReservedWhenFlowableStartFails() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString())).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap()))
                .thenThrow(new IllegalStateException("engine down"));

        assertThatThrownBy(() -> orchestrator.start(dossierId, PROCESS, Map.of(), "idem-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("engine down");
        verify(attachWorkflow).abortReserved(reserved.id(), AbortReservedReason.ORPHAN_START_FAILED);
        verify(attachWorkflow, never()).confirmStarted(any(), any());
        verify(idempotencyStore, never()).remember(any(), any(), any(), any());
    }

    @Test
    void start_rejectsReservedVariableKeysBeforeReserve() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() ->
                        orchestrator.start(dossierId, PROCESS, Map.of("tenantId", "x"), "idem-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(attachWorkflow, never()).reserve(any(), any(), any());
    }

    private static WorkflowTypeConfig config(DossierStatus onStart) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                PROCESS,
                "Smoke",
                List.of(
                        new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE),
                        new WorkflowStatusDefinition("COMPLETED", "Done", false, StatusOutcome.COMPLETED)),
                List.of(DossierStatus.OUVERT, DossierStatus.EN_COURS),
                onStart);
    }

    private static DossierWorkflowLink reservedLink(UUID dossierId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                null,
                PROCESS,
                1,
                "STARTED",
                "manual:idem-1",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null);
    }

    private static Dossier dossier(UUID id) {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "WF",
                "Label",
                DossierStatus.OUVERT,
                Map.of(),
                null,
                0,
                "u1",
                now,
                "u1",
                now,
                null,
                null);
    }
}
