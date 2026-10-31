package be.myrmacta.application.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.AbortReservedReason;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.AttachWorkflowUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.model.StartVariableDeclaration;
import be.myrmacta.domain.workflow.port.out.IdempotencyStore;
import be.myrmacta.domain.workflow.port.out.StartVariableCatalogPort;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowEventPublisher;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowStartConfirmationPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ManualStartOrchestratorTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private DossierRepository dossierRepository;
    private DossierWorkflowLinkRepository linkRepository;
    private AttachWorkflowUseCase attachWorkflow;
    private WorkflowEnginePort workflowEnginePort;
    private IdempotencyStore idempotencyStore;
    private WorkflowStartConfirmationPort workflowStartConfirmer;
    private WorkflowEventPublisher eventPublisher;
    private DossierTypeRepository dossierTypeRepository;
    private StartVariableCatalogPort startVariableCatalog;
    private ManualStartOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        dossierRepository = mock(DossierRepository.class);
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        idempotencyStore = mock(IdempotencyStore.class);
        workflowStartConfirmer = mock(WorkflowStartConfirmationPort.class);
        eventPublisher = mock(WorkflowEventPublisher.class);
        dossierTypeRepository = mock(DossierTypeRepository.class);
        startVariableCatalog = mock(StartVariableCatalogPort.class);
        when(startVariableCatalog.findDeclared(anyString())).thenReturn(List.of());
        orchestrator = new ManualStartOrchestrator(
                dossierRepository,
                dossierTypeRepository,
                linkRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                workflowStartConfirmer,
                eventPublisher,
                startVariableCatalog);
    }

    @Test
    void start_reservesStartsAndDelegatesConfirmationAtomically() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString(), eq("starter-sub"))).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap()))
                .thenReturn("proc-1");
        when(linkRepository.findById(reserved.id()))
                .thenReturn(Optional.of(reserved.withProcessInstance("proc-1")));

        ManualStartOrchestrator.StartResult result =
                orchestrator.start(dossierId, PROCESS, Map.of("priority", "HIGH"), "idem-1", "starter-sub");

        assertThat(result.replay()).isFalse();
        assertThat(result.link().isActive()).isTrue();
        verify(workflowEnginePort).start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap());
        verify(attachWorkflow).reserve(dossierId, PROCESS, "manual:idem-1", "starter-sub");
        // TX2 (confirm + idempotency + onStart) est déléguée à un seul appel transactionnel.
        verify(workflowStartConfirmer).confirm(dossier, reserved, "proc-1", PROCESS, "idem-1");
        verify(eventPublisher).publish(any());
    }

    /** TODO §113 (D-GROUP-LINK) — le groupe destinataire est posé sur le lien AVANT le démarrage : la première tâche le voit. */
    @Test
    void start_withAGroupRecordsItOnTheLinkBeforeTheEngineStarts() {
        WorkflowLinkGroupRepository linkGroups = mock(WorkflowLinkGroupRepository.class);
        ManualStartOrchestrator withGroups = new ManualStartOrchestrator(
                dossierRepository,
                dossierTypeRepository,
                linkRepository,
                attachWorkflow,
                workflowEnginePort,
                idempotencyStore,
                workflowStartConfirmer,
                eventPublisher,
                startVariableCatalog,
                linkGroups);
        UUID dossierId = UUID.randomUUID();
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString(), eq("workflow"))).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap())).thenReturn("proc-1");
        when(linkRepository.findById(reserved.id())).thenReturn(Optional.of(reserved.withProcessInstance("proc-1")));

        withGroups.start(dossierId, PROCESS, Map.of(), "idem-1", "workflow", "COMPTABILITE");

        var order = org.mockito.Mockito.inOrder(linkGroups, workflowEnginePort);
        order.verify(linkGroups).assign(TENANT, reserved.id(), "COMPTABILITE");
        order.verify(workflowEnginePort).start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap());
    }

    @Test
    void start_withAGroupButNoGroupStoreIsRefusedBeforeAnyReservation() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() -> orchestrator.start(dossierId, PROCESS, Map.of(), "idem-1", "workflow", "COMPTABILITE"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(attachWorkflow, workflowEnginePort);
    }

    @Test
    void start_refusesAMissingDeclaredVariableBeforeAnyReservation() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(startVariableCatalog.findDeclared(PROCESS))
                .thenReturn(List.of(new StartVariableDeclaration("motif", "Motif", true)));

        assertThatThrownBy(() -> orchestrator.start(dossierId, PROCESS, Map.of("motif", "  "), "idem-1", "starter-sub"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motif");
        verifyNoInteractions(attachWorkflow, workflowEnginePort);
    }

    /**
     * Revue adversariale TODO §93 (I8) : documentType passé au démarrage (dialogue, API, MCP, dossier enfant) est soumis à
     * la même liste blanche du type de dossier qu'à la complétion d'une tâche (D-TASK-FORM-DOCTYPE-SCOPE).
     */
    @Test
    void start_refusesADocumentTypeNotAllowedForTheDossierTypeBeforeAnyReservation() {
        UUID dossierId = UUID.randomUUID();
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(dossierTypeRepository.findByCode(TENANT, "WF")).thenReturn(Optional.of(typeAllowing("facture")));

        assertThatThrownBy(() -> orchestrator.start(
                dossierId, PROCESS, Map.of("documentType", "secret"), "idem-1", "starter-sub"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("secret");
        verifyNoInteractions(attachWorkflow, workflowEnginePort);
    }

    @Test
    void start_acceptsAnAllowedDocumentType() {
        UUID dossierId = UUID.randomUUID();
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(dossierTypeRepository.findByCode(TENANT, "WF")).thenReturn(Optional.of(typeAllowing("facture")));
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString(), eq("starter-sub"))).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap())).thenReturn("proc-1");

        orchestrator.start(dossierId, PROCESS, Map.of("documentType", "facture"), "idem-1", "starter-sub");

        verify(workflowEnginePort).start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap());
    }

    private static DossierType typeAllowing(String... templateKeys) {
        return DossierType.inherit(UUID.randomUUID(), TENANT, "WF", "Type", PROCESS, true, true, java.util.List.of())
                .withUpdate("Type", PROCESS, true, true, java.util.List.of(), null, false, null,
                        java.util.List.of(templateKeys), "admin");
    }

    @Test
    void start_injectsDossierFieldsSnapshotIntoEngineVariables() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId, Map.of("montant", 42));
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString(), eq("starter-sub"))).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap()))
                .thenReturn("proc-1");
        when(linkRepository.findById(reserved.id()))
                .thenReturn(Optional.of(reserved.withProcessInstance("proc-1")));

        orchestrator.start(dossierId, PROCESS, Map.of("priority", "HIGH"), "idem-1", "starter-sub");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEnginePort).start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), captor.capture());
        assertThat(captor.getValue())
                .containsEntry(WorkflowVariables.DOSSIER_FIELDS, Map.of("montant", 42))
                .containsEntry("priority", "HIGH");
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
                orchestrator.start(dossierId, PROCESS, Map.of(), "idem-1", "starter-sub");

        assertThat(result.replay()).isTrue();
        assertThat(result.link().id()).isEqualTo(existing.id());
        verify(attachWorkflow, never()).reserve(any(), any(), any(), any());
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
        verifyNoInteractions(workflowStartConfirmer);
    }

    @Test
    void start_abortsReservedWhenFlowableStartFails() {
        UUID dossierId = UUID.randomUUID();
        Dossier dossier = dossier(dossierId);
        DossierWorkflowLink reserved = reservedLink(dossierId);
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(idempotencyStore.findLinkId(TENANT, dossierId, "idem-1")).thenReturn(Optional.empty());
        when(attachWorkflow.reserve(eq(dossierId), eq(PROCESS), anyString(), eq("starter-sub"))).thenReturn(reserved);
        when(workflowEnginePort.start(eq(PROCESS), eq(TENANT), eq(reserved.id().toString()), anyMap()))
                .thenThrow(new IllegalStateException("engine down"));

        assertThatThrownBy(() -> orchestrator.start(dossierId, PROCESS, Map.of(), "idem-1", "starter-sub"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("engine down");
        verify(attachWorkflow).abortReserved(reserved.id(), AbortReservedReason.ORPHAN_START_FAILED);
        verifyNoInteractions(workflowStartConfirmer);
    }

    @Test
    void start_rejectsReservedVariableKeysBeforeReserve() {
        UUID dossierId = UUID.randomUUID();

        assertThatThrownBy(() ->
                        orchestrator.start(dossierId, PROCESS, Map.of("tenantId", "x"), "idem-1", "starter-sub"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved");
        verify(attachWorkflow, never()).reserve(any(), any(), any(), any());
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
                null,
                "starter-sub");
    }

    private static Dossier dossier(UUID id) {
        return dossier(id, Map.of());
    }

    private static Dossier dossier(UUID id, Map<String, Object> fields) {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "WF",
                "Label",
                DossierStatus.OUVERT,
                fields,
                null,
                0,
                "u1",
                now,
                "u1",
                now,
                null,
                null,
                null);
    }
}
