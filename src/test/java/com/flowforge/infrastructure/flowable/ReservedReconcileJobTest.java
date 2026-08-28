package com.flowforge.infrastructure.flowable;

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
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.config.WorkflowProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservedReconcileJobTest {

    private DossierWorkflowLinkRepository linkRepository;
    private AttachWorkflowUseCase attachWorkflow;
    private WorkflowEnginePort workflowEnginePort;
    private DossierRepository dossierRepository;
    private ReservedReconcileJob job;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        attachWorkflow = mock(AttachWorkflowUseCase.class);
        workflowEnginePort = mock(WorkflowEnginePort.class);
        dossierRepository = mock(DossierRepository.class);
        WorkflowProperties properties = new WorkflowProperties(
                Duration.ofMinutes(5),
                new WorkflowProperties.Outbox(20, 5, Duration.ofSeconds(1), Duration.ofDays(7)),
                Duration.ofDays(7));
        job = new ReservedReconcileJob(linkRepository, attachWorkflow, workflowEnginePort, dossierRepository, properties);
    }

    private static Dossier dossier(UUID id, Map<String, Object> fields) {
        Instant now = Instant.parse("2026-08-02T10:00:00Z");
        return new Dossier(
                id, "tenant_dev", "DOS-1", "WF", "Label", DossierStatus.OUVERT, fields, null, 0, "u1", now, "u1",
                now, null, null, null);
    }

    @Test
    void reconcile_confirmsWhenInstanceFoundByBusinessKey() {
        DossierWorkflowLink link = reservedLink();
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString()))
                .thenReturn(Optional.of("proc-existing"));

        job.reconcile(link);

        verify(attachWorkflow).confirmStarted(link.id(), "proc-existing");
        verify(workflowEnginePort, never()).start(any(), any(), any(), any());
    }

    @Test
    void reconcile_retriesStartAndConfirmsWhenNoExistingInstance() {
        DossierWorkflowLink link = reservedLink();
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString()))
                .thenReturn(Optional.empty());
        when(dossierRepository.findById(link.dossierId()))
                .thenReturn(Optional.of(dossier(link.dossierId(), Map.of("montant", 42))));
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap())).thenReturn("proc-retry");

        job.reconcile(link);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workflowEnginePort)
                .start(eq(link.processDefinitionKey()), eq(link.tenantId()), eq(link.id().toString()), captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue())
                .containsEntry(WorkflowVariables.DOSSIER_FIELDS, Map.of("montant", 42));
        verify(attachWorkflow).confirmStarted(link.id(), "proc-retry");
    }

    @Test
    void reconcile_abortsWithOrphanTimeoutWhenRetryStartFails() {
        DossierWorkflowLink link = reservedLink();
        when(workflowEnginePort.findProcessInstanceIdByBusinessKey(link.id().toString()))
                .thenReturn(Optional.empty());
        when(workflowEnginePort.start(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new IllegalStateException("still missing"));

        job.reconcile(link);

        verify(attachWorkflow).abortReserved(link.id(), AbortReservedReason.ORPHAN_TIMEOUT);
        verify(attachWorkflow, never()).confirmStarted(any(), any());
    }

    private static DossierWorkflowLink reservedLink() {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "tenant_dev",
                null,
                "smokeProcess",
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                null);
    }
}
