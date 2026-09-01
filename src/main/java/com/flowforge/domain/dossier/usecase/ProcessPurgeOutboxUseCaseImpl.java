package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.port.out.DocumentStoragePort;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
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
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class ProcessPurgeOutboxUseCaseImpl implements ProcessPurgeOutboxUseCase {

    private static final int MAX_ERROR = 1024;

    private final PurgeOutboxRepository purgeOutboxRepository;
    private final DossierRepository dossierRepository;
    private final DossierElementRepository elementRepository;
    private final DossierRelationRepository relationRepository;
    private final DossierWorkflowLinkRepository workflowLinkRepository;
    private final DocumentStoragePort documentStoragePort;
    private final WorkflowEnginePort workflowEnginePort;

    public ProcessPurgeOutboxUseCaseImpl(
            PurgeOutboxRepository purgeOutboxRepository,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierRelationRepository relationRepository,
            DossierWorkflowLinkRepository workflowLinkRepository,
            DocumentStoragePort documentStoragePort,
            WorkflowEnginePort workflowEnginePort) {
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.workflowLinkRepository = Objects.requireNonNull(workflowLinkRepository, "workflowLinkRepository");
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
    }

    @Override
    public int processBatch(Instant now, int limit, Function<UUID, Runnable> onItemStart) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(onItemStart, "onItemStart");
        if (limit <= 0) {
            return 0;
        }
        int processed = 0;
        for (PurgeOutboxRecord record : purgeOutboxRepository.findRetryable(limit)) {
            if (shouldSkipClock(record, now)) {
                continue;
            }
            if (!purgeOutboxRepository.claim(record.id())) {
                continue;
            }
            Runnable closeItemScope = onItemStart.apply(record.dossierId());
            try {
                purgeDossier(record);
                purgeOutboxRepository.markCompleted(record.id());
                processed++;
            } catch (RuntimeException ex) {
                purgeOutboxRepository.markFailed(record.id(), truncate(ex.getMessage()));
                processed++;
            } finally {
                closeItemScope.run();
            }
        }
        return processed;
    }

    @Override
    public int recoverStaleProcessing(Instant olderThan) {
        Objects.requireNonNull(olderThan, "olderThan");
        return purgeOutboxRepository.requeueStaleProcessing(olderThan);
    }

    private static boolean shouldSkipClock(PurgeOutboxRecord record, Instant now) {
        if (record.origin() != PurgeOutboxOrigin.RETENTION) {
            return false;
        }
        Instant eligibleAt = PurgeOutboxPayloads.parseEligibleAt(record.payloadJson());
        return eligibleAt.isAfter(now);
    }

    private void purgeDossier(PurgeOutboxRecord record) {
        UUID dossierId = record.dossierId();
        Optional<Dossier> dossier = dossierRepository.findById(dossierId);
        String tenantId = dossier.map(Dossier::tenantId)
                .orElseGet(() -> PurgeOutboxPayloads.parseTenantId(record.payloadJson()));
        if (tenantId == null || tenantId.isBlank()) {
            throw new DocumentStorageException("Missing tenantId for purge of dossier " + dossierId);
        }

        for (DossierElement element : elementRepository.findByDossierIdOrderByAddedAtAsc(dossierId)) {
            String ref = element.storageRef();
            if (ref != null && !ref.isBlank()) {
                documentStoragePort.delete(tenantId, ref);
            }
        }

        for (DossierWorkflowLink link : workflowLinkRepository.findByDossierIdOrderBySequence(dossierId)) {
            if (link.processInstanceId() != null && !link.processInstanceId().isBlank()) {
                workflowEnginePort.deleteProcessInstanceBestEffort(link.processInstanceId());
            }
        }

        elementRepository.deleteAllByDossierId(dossierId);
        relationRepository.deleteRelationsTouching(dossierId);
        workflowLinkRepository.deleteAllByDossierId(dossierId);
        if (dossier.isPresent()) {
            dossierRepository.deleteById(dossierId);
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "purge failed";
        }
        return message.length() <= MAX_ERROR ? message : message.substring(0, MAX_ERROR);
    }
}
