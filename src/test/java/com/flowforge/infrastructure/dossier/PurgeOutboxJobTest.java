package com.flowforge.infrastructure.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxPayloads;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.dossier.usecase.ProcessPurgeOutboxUseCaseImpl;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.config.PurgeProperties;
import com.flowforge.infrastructure.config.TenantProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

class PurgeOutboxJobTest {

    @Test
    void process_delegatesNowAndBatchSize() {
        ProcessPurgeOutboxUseCase useCase = mock(ProcessPurgeOutboxUseCase.class);
        when(useCase.processBatch(any(Instant.class), eq(50), any())).thenReturn(2);
        PurgeOutboxJob job = new PurgeOutboxJob(
                useCase,
                new PurgeProperties(Duration.ofHours(1), 50, Duration.ofHours(2)),
                new TenantProperties("tenant_dev"));

        job.process();

        verify(useCase).processBatch(any(Instant.class), eq(50), any());
    }

    @Test
    void process_recoversStaleProcessingBeforeProcessingBatch() {
        ProcessPurgeOutboxUseCase useCase = mock(ProcessPurgeOutboxUseCase.class);
        when(useCase.recoverStaleProcessing(any(Instant.class))).thenReturn(1);
        when(useCase.processBatch(any(Instant.class), eq(50), any())).thenReturn(0);
        PurgeOutboxJob job = new PurgeOutboxJob(
                useCase,
                new PurgeProperties(Duration.ofHours(1), 50, Duration.ofHours(2)),
                new TenantProperties("tenant_dev"));

        job.process();

        verify(useCase).recoverStaleProcessing(any(Instant.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void process_perItemCallback_putsAndRemovesDossierIdInMdc() {
        ProcessPurgeOutboxUseCase useCase = mock(ProcessPurgeOutboxUseCase.class);
        ArgumentCaptor<Function<UUID, Runnable>> onItemStart = ArgumentCaptor.forClass(Function.class);
        when(useCase.processBatch(any(Instant.class), eq(50), onItemStart.capture())).thenReturn(0);
        PurgeOutboxJob job = new PurgeOutboxJob(
                useCase,
                new PurgeProperties(Duration.ofHours(1), 50, Duration.ofHours(2)),
                new TenantProperties("tenant_dev"));

        job.process();

        // job.process() has already returned here, so the batch-level MdcScope.tenant(...) is
        // already closed — this test exercises only the per-item callback's own scope, independent
        // of the batch scope's lifecycle (proven live, during process(), by T-INT-1 below).
        UUID dossierId = UUID.randomUUID();
        Runnable closeItemScope = onItemStart.getValue().apply(dossierId);
        assertThat(MDC.get("dossierId")).isEqualTo(dossierId.toString());

        closeItemScope.run();

        assertThat(MDC.get("dossierId")).isNull();
    }

    @Test
    void process_multipleItems_eachSeesOwnDossierIdWithTenantIdStillPresentNoLeakBetweenItems() {
        String tenantId = "tenant_dev";
        UUID dossierId1 = UUID.randomUUID();
        UUID dossierId2 = UUID.randomUUID();
        PurgeOutboxRecord record1 = pending(dossierId1, tenantId, NOW());
        PurgeOutboxRecord record2 = pending(dossierId2, tenantId, NOW());

        PurgeOutboxRepository outbox = mock(PurgeOutboxRepository.class);
        DossierRepository dossiers = mock(DossierRepository.class);
        DossierElementRepository elements = mock(DossierElementRepository.class);
        DossierRelationRepository relations = mock(DossierRelationRepository.class);
        DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
        DocumentStoragePort storage = mock(DocumentStoragePort.class);
        WorkflowEnginePort engine = mock(WorkflowEnginePort.class);
        when(outbox.findRetryable(50)).thenReturn(List.of(record1, record2));
        when(outbox.claim(any())).thenReturn(true);
        when(elements.findByDossierIdOrderByAddedAtAsc(any())).thenReturn(List.of());
        when(dossiers.findById(dossierId1)).thenReturn(Optional.of(archived(dossierId1, tenantId)));
        when(dossiers.findById(dossierId2)).thenReturn(Optional.of(archived(dossierId2, tenantId)));
        when(links.findByDossierIdOrderBySequence(dossierId1))
                .thenReturn(List.of(link(dossierId1, "pi-1")));
        when(links.findByDossierIdOrderBySequence(dossierId2))
                .thenReturn(List.of(link(dossierId2, "pi-2")));

        Map<String, String> mdcDossierIdSeenAt = new java.util.concurrent.ConcurrentHashMap<>();
        Map<String, String> mdcTenantIdSeenAt = new java.util.concurrent.ConcurrentHashMap<>();
        org.mockito.Mockito.doAnswer(invocation -> {
                    String processInstanceId = invocation.getArgument(0);
                    mdcDossierIdSeenAt.put(processInstanceId, MDC.get("dossierId"));
                    mdcTenantIdSeenAt.put(processInstanceId, MDC.get("tenantId"));
                    return null;
                })
                .when(engine)
                .deleteProcessInstanceBestEffort(any());

        ProcessPurgeOutboxUseCase useCase = new ProcessPurgeOutboxUseCaseImpl(
                outbox, dossiers, elements, relations, links, storage, engine);
        PurgeOutboxJob job = new PurgeOutboxJob(
                useCase,
                new PurgeProperties(Duration.ofHours(1), 50, Duration.ofHours(2)),
                new TenantProperties(tenantId));

        job.process();

        assertThat(mdcDossierIdSeenAt).containsEntry("pi-1", dossierId1.toString());
        assertThat(mdcDossierIdSeenAt).containsEntry("pi-2", dossierId2.toString());
        assertThat(mdcTenantIdSeenAt).containsEntry("pi-1", tenantId);
        assertThat(mdcTenantIdSeenAt).containsEntry("pi-2", tenantId);
        assertThat(MDC.get("dossierId")).isNull();
        assertThat(MDC.get("tenantId")).isNull();
    }

    private static Instant NOW() {
        return Instant.parse("2026-08-19T12:00:00Z");
    }

    private static PurgeOutboxRecord pending(UUID dossierId, String tenantId, Instant now) {
        return PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossierId,
                "DOSSIER_PURGE_REQUESTED",
                PurgeOutboxOrigin.ADMIN,
                PurgeOutboxPayloads.json(tenantId, dossierId, "CLAIM", now),
                now);
    }

    private static Dossier archived(UUID id, String tenantId) {
        Instant now = NOW();
        return new Dossier(
                id, tenantId, "DOS-1", "CLAIM", "Dossier", DossierStatus.ARCHIVE, Map.of(), null, 0,
                "creator", now, "job", now, "closer", now, now);
    }

    private static DossierWorkflowLink link(UUID dossierId, String processInstanceId) {
        Instant now = NOW();
        return new DossierWorkflowLink(
                UUID.randomUUID(), dossierId, "tenant_dev", processInstanceId, "wf", 1, "ACTIVE",
                "start", now, null, null, "user");
    }
}
