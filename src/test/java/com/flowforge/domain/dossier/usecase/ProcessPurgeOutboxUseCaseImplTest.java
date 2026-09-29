package com.flowforge.domain.dossier.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.PurgeOutboxOrigin;
import com.flowforge.domain.dossier.model.PurgeOutboxPayloads;
import com.flowforge.domain.dossier.model.PurgeOutboxRecord;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRelationRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.dossier.port.out.PurgeOutboxRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProcessPurgeOutboxUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");
    private static final String TENANT = "tenant_dev";
    private static final Function<UUID, Runnable> NO_OP = id -> () -> {};

    private PurgeOutboxRepository outbox;
    private DossierRepository dossiers;
    private DossierElementRepository elements;
    private DossierRelationRepository relations;
    private DossierWorkflowLinkRepository links;
    private DocumentStoragePort storage;
    private WorkflowEnginePort engine;
    private ProcessPurgeOutboxUseCaseImpl useCase;
    private com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository answers;
    private com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository portalLinks;
    private com.flowforge.domain.signature.port.out.SignatureRequestRepository signatures;

    @BeforeEach
    void setUp() {
        outbox = mock(PurgeOutboxRepository.class);
        dossiers = mock(DossierRepository.class);
        elements = mock(DossierElementRepository.class);
        relations = mock(DossierRelationRepository.class);
        links = mock(DossierWorkflowLinkRepository.class);
        storage = mock(DocumentStoragePort.class);
        engine = mock(WorkflowEnginePort.class);
        answers = mock(com.flowforge.domain.workflow.port.out.TaskQuestionAnswerRepository.class);
        signatures = mock(com.flowforge.domain.signature.port.out.SignatureRequestRepository.class);
        portalLinks = mock(com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository.class);
        useCase = new ProcessPurgeOutboxUseCaseImpl(
                outbox, dossiers, elements, relations, links, storage, engine, answers, portalLinks, signatures);
        when(outbox.claim(any())).thenReturn(true);
        when(elements.findByDossierIdOrderByAddedAtAsc(any())).thenReturn(List.of());
        when(links.findByDossierIdOrderBySequence(any())).thenReturn(List.of());
    }

    @Test
    void processBatch_happyPath_deletesStoreThenGraphAndCompletes() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.RETENTION, NOW.minusSeconds(60));
        Dossier dossier = archived(dossierId);
        DossierElement attachment = element(dossierId, TENANT + "/doc.pdf");
        DossierElement note = element(dossierId, null);
        DossierWorkflowLink link = link(dossierId, "pi-1");
        when(outbox.findRetryable(50)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId)).thenReturn(List.of(attachment, note));
        when(links.findByDossierIdOrderBySequence(dossierId)).thenReturn(List.of(link));

        int processed = useCase.processBatch(NOW, 50, NO_OP);

        assertThat(processed).isEqualTo(1);
        verify(storage).delete(TENANT, TENANT + "/doc.pdf");
        verify(engine).deleteProcessInstanceBestEffort("pi-1");
        verify(elements).deleteAllByDossierId(dossierId);
        verify(signatures).deleteAllByDossierId(dossierId); // S-PURGE-1
        // revue adversariale n°15 : les demandes partent AVANT les instances de process, sinon leur suppression
        // déclencherait annulations et notifications parasites sur un dossier en cours de purge
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(signatures, engine);
        order.verify(signatures).deleteAllByDossierId(dossierId);
        order.verify(engine).deleteProcessInstanceBestEffort("pi-1");
        verify(relations).deleteRelationsTouching(dossierId);
        verify(links).deleteAllByDossierId(dossierId);
        verify(dossiers).deleteById(dossierId);
        verify(outbox).markCompleted(record.id());
        verify(outbox, never()).markFailed(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void processBatch_invokesOnItemStartWithDossierIdBeforePurgingThenRunsCloserAfterCompletion() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.RETENTION, NOW.minusSeconds(60));
        DossierWorkflowLink link = link(dossierId, "pi-1");
        when(outbox.findRetryable(50)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(archived(dossierId)));
        when(links.findByDossierIdOrderBySequence(dossierId)).thenReturn(List.of(link));
        Function<UUID, Runnable> onItemStart = mock(Function.class);
        Runnable closer = mock(Runnable.class);
        when(onItemStart.apply(dossierId)).thenReturn(closer);

        useCase.processBatch(NOW, 50, onItemStart);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(onItemStart, engine, outbox, closer);
        order.verify(onItemStart).apply(dossierId);
        order.verify(engine).deleteProcessInstanceBestEffort("pi-1");
        order.verify(outbox).markCompleted(record.id());
        order.verify(closer).run();
    }

    @SuppressWarnings("unchecked")
    @Test
    void processBatch_itemFails_stillRunsCloserAfterMarkFailed() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(archived(dossierId)));
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId))
                .thenReturn(List.of(element(dossierId, TENANT + "/doc.pdf")));
        org.mockito.Mockito.doThrow(new DocumentStorageException("S3 down"))
                .when(storage)
                .delete(TENANT, TENANT + "/doc.pdf");
        Function<UUID, Runnable> onItemStart = mock(Function.class);
        Runnable closer = mock(Runnable.class);
        when(onItemStart.apply(dossierId)).thenReturn(closer);

        useCase.processBatch(NOW, 10, onItemStart);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(onItemStart, outbox, closer);
        order.verify(onItemStart).apply(dossierId);
        order.verify(outbox).markFailed(eq(record.id()), eq("S3 down"));
        order.verify(closer).run();
    }

    @SuppressWarnings("unchecked")
    @Test
    void processBatch_itemSkippedByClock_neverInvokesOnItemStart() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.RETENTION, NOW.plusSeconds(3600));
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        Function<UUID, Runnable> onItemStart = mock(Function.class);

        useCase.processBatch(NOW, 10, onItemStart);

        verify(onItemStart, never()).apply(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void processBatch_claimLost_neverInvokesOnItemStart() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(outbox.claim(record.id())).thenReturn(false);
        Function<UUID, Runnable> onItemStart = mock(Function.class);

        useCase.processBatch(NOW, 10, onItemStart);

        verify(onItemStart, never()).apply(any());
    }

    @Test
    void processBatch_retentionFutureEligibleAt_skipsWithoutClaim() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.RETENTION, NOW.plusSeconds(3600));
        when(outbox.findRetryable(10)).thenReturn(List.of(record));

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isZero();
        verify(outbox, never()).claim(any());
        verify(storage, never()).delete(any(), any());
        verify(dossiers, never()).deleteById(any());
    }

    @Test
    void processBatch_adminOrigin_ignoresFutureClock() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW.plusSeconds(3600));
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(archived(dossierId)));

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isEqualTo(1);
        verify(outbox).claim(record.id());
        verify(outbox).markCompleted(record.id());
    }

    @Test
    void processBatch_storeFailure_marksFailedAndKeepsPostgres() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(archived(dossierId)));
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId))
                .thenReturn(List.of(element(dossierId, TENANT + "/doc.pdf")));
        org.mockito.Mockito.doThrow(new DocumentStorageException("S3 down"))
                .when(storage)
                .delete(TENANT, TENANT + "/doc.pdf");

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isEqualTo(1);
        verify(outbox).markFailed(eq(record.id()), eq("S3 down"));
        verify(elements, never()).deleteAllByDossierId(any());
        verify(dossiers, never()).deleteById(any());
        verify(outbox, never()).markCompleted(any());
    }

    @Test
    void processBatch_secondPass_isIdempotentWhenGraphAlreadyGone() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.empty());

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isEqualTo(1);
        verify(elements).deleteAllByDossierId(dossierId);
        verify(relations).deleteRelationsTouching(dossierId);
        verify(links).deleteAllByDossierId(dossierId);
        verify(dossiers, never()).deleteById(any());
        verify(outbox).markCompleted(record.id());
    }

    @Test
    void processBatch_zeroLimit_isNoOp() {
        assertThat(useCase.processBatch(NOW, 0, NO_OP)).isZero();
        verify(outbox, never()).findRetryable(anyInt());
    }

    @Test
    void processBatch_claimLost_skipsWithoutDeletes() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(outbox.claim(record.id())).thenReturn(false);

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isZero();
        verify(storage, never()).delete(any(), any());
        verify(outbox, never()).markCompleted(any());
        verify(outbox, never()).markFailed(any(), any());
    }

    @Test
    void processBatch_postgresFailureAfterStore_marksFailed() {
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.ADMIN, NOW);
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(archived(dossierId)));
        when(elements.findByDossierIdOrderByAddedAtAsc(dossierId))
                .thenReturn(List.of(element(dossierId, TENANT + "/doc.pdf")));
        org.mockito.Mockito.doThrow(new IllegalStateException("constraint"))
                .when(elements)
                .deleteAllByDossierId(dossierId);

        int processed = useCase.processBatch(NOW, 10, NO_OP);

        assertThat(processed).isEqualTo(1);
        verify(storage).delete(TENANT, TENANT + "/doc.pdf");
        verify(outbox).markFailed(eq(record.id()), eq("constraint"));
        verify(dossiers, never()).deleteById(any());
        verify(outbox, never()).markCompleted(any());
    }

    @Test
    void recoverStaleProcessing_delegatesToRepository() {
        Instant threshold = NOW.minusSeconds(7200);
        when(outbox.requeueStaleProcessing(threshold)).thenReturn(2);

        int recovered = useCase.recoverStaleProcessing(threshold);

        assertThat(recovered).isEqualTo(2);
        verify(outbox).requeueStaleProcessing(threshold);
    }

    private static PurgeOutboxRecord pending(UUID dossierId, PurgeOutboxOrigin origin, Instant eligibleAt) {
        return PurgeOutboxRecord.pending(
                UUID.randomUUID(),
                dossierId,
                "DOSSIER_PURGE_REQUESTED",
                origin,
                PurgeOutboxPayloads.json(TENANT, dossierId, "CLAIM", eligibleAt),
                NOW);
    }

    private static Dossier archived(UUID id) {
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "CLAIM",
                "Dossier",
                DossierStatus.ARCHIVE,
                Map.of(),
                null,
                0,
                "creator",
                NOW,
                "job",
                NOW,
                "closer",
                NOW,
                NOW);
    }

    private static DossierElement element(UUID dossierId, String storageRef) {
        return new DossierElement(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                storageRef == null ? ElementType.NOTE : ElementType.ATTACHMENT,
                "el",
                storageRef,
                Map.of(),
                "user",
                NOW,
                null);
    }

    private static DossierWorkflowLink link(UUID dossierId, String processInstanceId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                processInstanceId,
                "wf",
                1,
                "ACTIVE",
                "start",
                NOW,
                null,
                null,
                "user");
    }

    @Test
    void processBatch_alsoPurgesTaskAnswersAndPortalLinks_afterProcessInstances() {
        // T-PURGE-1 (TODO §68 D-PORTAL-PURGE) — y compris l'écart préexistant task_question_answer.
        UUID dossierId = UUID.randomUUID();
        PurgeOutboxRecord record = pending(dossierId, PurgeOutboxOrigin.RETENTION, NOW.minusSeconds(60));
        when(outbox.findRetryable(10)).thenReturn(List.of(record));
        when(dossiers.findById(dossierId)).thenReturn(java.util.Optional.of(archived(dossierId)));
        when(links.findByDossierIdOrderBySequence(dossierId)).thenReturn(List.of(link(dossierId, "pi-9")));

        useCase.processBatch(NOW, 10, NO_OP);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(engine, answers, portalLinks, dossiers);
        order.verify(engine).deleteProcessInstanceBestEffort("pi-9");
        order.verify(answers).deleteAllByDossierId(dossierId);
        order.verify(portalLinks).deleteAllByDossierId(dossierId);
        order.verify(dossiers).deleteById(dossierId);
    }
}
