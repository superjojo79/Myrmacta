package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AbortReservedReason;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.dossier.port.in.AttachWorkflowUseCase;
import com.flowforge.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class AttachWorkflowUseCaseImpl implements AttachWorkflowUseCase, GetDossierWorkflowHistoryUseCase {

    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final WorkflowTypeConfigRepository configRepository;

    public AttachWorkflowUseCaseImpl(
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowTypeConfigRepository configRepository) {
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.configRepository = Objects.requireNonNull(configRepository, "configRepository");
    }

    @Override
    public DossierWorkflowLink reserve(UUID dossierId, String processDefinitionKey, String triggerRef, String startedBy) {
        if (startedBy == null || startedBy.isBlank()) {
            throw new IllegalArgumentException("startedBy is required");
        }
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        WorkflowTypeConfig config = configRepository.findByWorkflowKey(dossier.tenantId(), processDefinitionKey)
                .orElseThrow(() -> new WorkflowTypeConfigNotFoundException(
                        "Workflow type config not found: " + processDefinitionKey));
        if (!config.allowsDossierStatus(dossier.status())) {
            throw new IllegalStateException("Dossier status " + dossier.status() + " cannot start " + processDefinitionKey);
        }
        linkRepository.findActiveByDossierId(dossierId)
                .ifPresent(active -> {
                    throw new WorkflowAlreadyRunningException(dossierId);
                });

        DossierWorkflowLink link = new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                dossier.tenantId(),
                null,
                processDefinitionKey,
                linkRepository.nextSequence(dossierId),
                config.initialStatusCode(),
                triggerRef,
                Instant.now(),
                null,
                null,
                startedBy);
        return linkRepository.save(link);
    }

    @Override
    public DossierWorkflowLink reserveOrResume(UUID dossierId, String processDefinitionKey, String triggerRef, String startedBy) {
        return linkRepository.findReservedByDossierAndProcess(dossierId, processDefinitionKey)
                .or(() -> linkRepository.findActiveByDossierId(dossierId)
                        .filter(link -> processDefinitionKey.equals(link.processDefinitionKey())))
                .orElseGet(() -> reserve(dossierId, processDefinitionKey, triggerRef, startedBy));
    }

    @Override
    public void confirmStarted(UUID linkId, String processInstanceId) {
        DossierWorkflowLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
        DossierWorkflowLink confirmed = linkRepository.save(link.withProcessInstance(processInstanceId));
        Dossier dossier = dossierRepository.findById(confirmed.dossierId())
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + confirmed.dossierId()));
        dossierRepository.save(dossier.withCurrentWorkflowLinkId(confirmed.id(), "workflow", Instant.now()));
    }

    @Override
    public void abortReserved(UUID linkId, AbortReservedReason reason) {
        DossierWorkflowLink link = linkRepository.findById(linkId)
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
        DossierWorkflowLink aborted = linkRepository.save(link.abort(reason, Instant.now()));
        Dossier dossier = dossierRepository.findById(aborted.dossierId())
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + aborted.dossierId()));
        if (aborted.id().equals(dossier.currentWorkflowLinkId())) {
            dossierRepository.save(dossier.withCurrentWorkflowLinkId(null, "workflow", Instant.now()));
        }
    }

    @Override
    public List<DossierWorkflowLink> getHistory(UUID dossierId) {
        return linkRepository.findByDossierIdOrderBySequence(dossierId);
    }
}
