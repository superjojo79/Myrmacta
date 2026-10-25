package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.document.model.DocumentStorageException;
import be.myrmacta.domain.document.port.out.DocumentStoragePort;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.PurgeOutboxOrigin;
import be.myrmacta.domain.dossier.model.PurgeOutboxPayloads;
import be.myrmacta.domain.dossier.model.PurgeOutboxRecord;
import be.myrmacta.domain.dossier.port.in.ProcessPurgeOutboxUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRelationRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.dossier.port.out.PurgeOutboxRepository;
import be.myrmacta.domain.signature.port.out.SignatureRequestRepository;
import be.myrmacta.domain.workflow.port.out.TaskQuestionAnswerRepository;
import be.myrmacta.domain.workflow.portal.port.out.PortalLinkRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
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
    private final TaskQuestionAnswerRepository taskQuestionAnswerRepository;
    private final PortalLinkRepository portalLinkRepository;
    private final SignatureRequestRepository signatureRequestRepository;

    public ProcessPurgeOutboxUseCaseImpl(
            PurgeOutboxRepository purgeOutboxRepository,
            DossierRepository dossierRepository,
            DossierElementRepository elementRepository,
            DossierRelationRepository relationRepository,
            DossierWorkflowLinkRepository workflowLinkRepository,
            DocumentStoragePort documentStoragePort,
            WorkflowEnginePort workflowEnginePort,
            TaskQuestionAnswerRepository taskQuestionAnswerRepository,
            PortalLinkRepository portalLinkRepository,
            SignatureRequestRepository signatureRequestRepository) {
        this.purgeOutboxRepository = Objects.requireNonNull(purgeOutboxRepository, "purgeOutboxRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.relationRepository = Objects.requireNonNull(relationRepository, "relationRepository");
        this.workflowLinkRepository = Objects.requireNonNull(workflowLinkRepository, "workflowLinkRepository");
        this.documentStoragePort = Objects.requireNonNull(documentStoragePort, "documentStoragePort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.taskQuestionAnswerRepository =
                Objects.requireNonNull(taskQuestionAnswerRepository, "taskQuestionAnswerRepository");
        this.portalLinkRepository = Objects.requireNonNull(portalLinkRepository, "portalLinkRepository");
        this.signatureRequestRepository =
                Objects.requireNonNull(signatureRequestRepository, "signatureRequestRepository");
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

        // TODO §72 : demandes de signature et signataires (adresses e-mail des tiers), supprimés AVANT les instances
        // de process : sinon leur suppression déclencherait l'annulation des demandes ouvertes et des notifications
        // parasites sur un dossier en cours de purge. Les documents signés et preuves sont des PRODUCED_DOC :
        // déjà supprimés du stockage avec les éléments.
        signatureRequestRepository.deleteAllByDossierId(dossierId);

        for (DossierWorkflowLink link : workflowLinkRepository.findByDossierIdOrderBySequence(dossierId)) {
            if (link.processInstanceId() != null && !link.processInstanceId().isBlank()) {
                workflowEnginePort.deleteProcessInstanceBestEffort(link.processInstanceId());
            }
        }

        elementRepository.deleteAllByDossierId(dossierId);
        // TODO §68 D-PORTAL-PURGE : réponses user-question (écart préexistant) et liens portail
        // (adresse e-mail du tiers) — après la suppression des instances de process, qui révoque
        // déjà les liens vivants via le listener portail.
        taskQuestionAnswerRepository.deleteAllByDossierId(dossierId);
        portalLinkRepository.deleteAllByDossierId(dossierId);
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
