package com.flowforge.domain.workflow.portal.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.portal.model.PortalLink;
import com.flowforge.domain.workflow.portal.model.PortalLinkStatusReason;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import com.flowforge.domain.workflow.portal.port.in.ManagePortalLinkUseCase;
import com.flowforge.domain.workflow.portal.port.out.PortalAvailabilityPort;
import com.flowforge.domain.workflow.portal.port.out.PortalIdentityRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalLinkRepository;
import com.flowforge.domain.workflow.portal.port.out.PortalTaskLookupPort;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * TODO §68 US-5 — l'envoi relit la source d'adresse ({@link IssuePortalLinkUseCaseImpl#issue}) : une
 * correction du champ par l'instructeur est prise en compte (D-PORTAL-NO-ADDRESS). Le renvoi révoque
 * d'abord le lien vivant (exigence H2 : un seul lien vivant par tâche) — l'ancien jeton devient
 * inutilisable immédiatement. La tâche doit être ouverte, externe, et appartenir à ce dossier
 * (jamais de lien vers la tâche d'un autre dossier).
 */
public final class ManagePortalLinkUseCaseImpl implements ManagePortalLinkUseCase {

    private final String tenantId;
    private final PortalLinkRepository repository;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final PortalTaskLookupPort taskLookup;
    private final WorkflowEnginePort workflowEnginePort;
    private final IssuePortalLinkUseCaseImpl issuer;
    private final PortalAvailabilityPort availability;
    private final Clock clock;
    private final PortalIdentityRepository identities;

    public ManagePortalLinkUseCaseImpl(
            String tenantId,
            PortalLinkRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            PortalTaskLookupPort taskLookup,
            WorkflowEnginePort workflowEnginePort,
            IssuePortalLinkUseCaseImpl issuer,
            PortalAvailabilityPort availability,
            Clock clock) {
        this(tenantId, repository, dossierRepository, accessPolicy, taskLookup, workflowEnginePort, issuer, availability,
                clock, null);
    }

    /** {@code identities} : traces d'authentification forte montrées à l'instructeur (TODO §103) ; nul si absentes. */
    public ManagePortalLinkUseCaseImpl(
            String tenantId,
            PortalLinkRepository repository,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            PortalTaskLookupPort taskLookup,
            WorkflowEnginePort workflowEnginePort,
            IssuePortalLinkUseCaseImpl issuer,
            PortalAvailabilityPort availability,
            Clock clock,
            PortalIdentityRepository identities) {
        this.identities = identities;
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.taskLookup = Objects.requireNonNull(taskLookup, "taskLookup");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.availability = Objects.requireNonNull(availability, "availability");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PortalLinks list(UUID dossierId, AccessContext.User actor) {
        Dossier dossier = requireDossier(dossierId);
        accessPolicy.assertCanView(dossier, actor);
        List<PortalLink> links = repository.findByDossierId(dossierId);
        return new PortalLinks(accessPolicy.canEdit(dossier, actor), links, identities == null ? Map.of()
                : identities.tracesByLinkId(links.stream().map(PortalLink::id).toList()));
    }

    @Override
    public PortalLink send(UUID dossierId, String taskId, AccessContext.User actor) {
        Dossier dossier = requireDossier(dossierId);
        accessPolicy.assertCanEdit(dossier, actor);
        requirePortalEnabled();
        PortalTaskLookupPort.OpenTask task = taskLookup.findOpenTask(taskId)
                .orElseThrow(() -> new IllegalStateException("Task is not open: " + taskId));
        String taskDossierId = workflowEnginePort.findDossierIdVariable(task.processInstanceId()).orElse(null);
        if (!dossierId.toString().equals(taskDossierId)) {
            throw new IllegalArgumentException("Task " + taskId + " does not belong to dossier " + dossierId);
        }
        PortalTaskDefinition definition = taskLookup.findExternalTaskDefinition(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task " + taskId + " is not an external portal task"));
        for (PortalLink live : repository.findLiveByTaskId(taskId)) {
            repository.transition(live, live.revoked(clock.instant(), actor.userId(), PortalLinkStatusReason.RESENT));
        }
        return issuer.issue(taskId, task.processInstanceId(), dossierId, definition.schemaKey(),
                definition.recipientSource(), definition.ttlDays(), definition.identityField(), actor.userId());
    }

    @Override
    public PortalLink revoke(UUID dossierId, UUID linkId, AccessContext.User actor) {
        Dossier dossier = requireDossier(dossierId);
        accessPolicy.assertCanEdit(dossier, actor);
        PortalLink link = repository.findById(linkId)
                .filter(l -> l.dossierId().equals(dossierId) && tenantId.equals(l.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Portal link not found: " + linkId));
        if (!link.status().isLive()) {
            throw new IllegalStateException("Portal link is not live: " + link.status());
        }
        PortalLink revoked = link.revoked(clock.instant(), actor.userId(), PortalLinkStatusReason.REVOKED_BY_USER);
        if (!repository.transition(link, revoked)) {
            throw new IllegalStateException("Portal link changed concurrently: " + linkId);
        }
        return revoked;
    }

    private Dossier requireDossier(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .filter(d -> tenantId.equals(d.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
    }

    private void requirePortalEnabled() {
        if (!availability.isPortalEnabled()) {
            throw new IllegalStateException("The external portal is disabled on this instance");
        }
    }
}
