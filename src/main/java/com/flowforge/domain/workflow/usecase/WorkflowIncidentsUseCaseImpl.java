package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.IncidentNotRetryableException;
import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.TenantIncident;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException;
import com.flowforge.domain.workflow.port.in.WorkflowIncidentsUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowIncidentPort;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * TODO §94 — échecs des jobs du moteur rattachés aux dossiers. Garde de lecture identique aux tâches d'une exécution
 * (participants, éditeurs du dossier, TA/WA — D-ASSIGN-CLAIM) ; message de l'exception réservé à TA/WA
 * (D-INCIDENT-MESSAGE) ; rejeu par TA/WA d'un incident (dead letter) de l'exécution visée seulement
 * (D-INCIDENT-RETRY-SCOPE).
 */
public final class WorkflowIncidentsUseCaseImpl implements WorkflowIncidentsUseCase {

    /** Tentatives rendues à un job rejoué : le défaut du moteur. */
    public static final int RETRIES_ON_RETRY = 3;
    /** D-INCIDENT-SCAN : la liste du tenant lit au plus ce nombre d'échecs (les plus récents). */
    public static final int TENANT_SCAN_LIMIT = 1000;

    private final String tenantId;
    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final WorkflowParticipantPort participantPort;
    private final DossierAccessPolicy dossierAccessPolicy;
    private final WorkflowIncidentPort incidentPort;

    public WorkflowIncidentsUseCaseImpl(
            String tenantId,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            WorkflowParticipantPort participantPort,
            DossierAccessPolicy dossierAccessPolicy,
            WorkflowIncidentPort incidentPort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.dossierAccessPolicy = Objects.requireNonNull(dossierAccessPolicy, "dossierAccessPolicy");
        this.incidentPort = Objects.requireNonNull(incidentPort, "incidentPort");
    }

    @Override
    public List<WorkflowIncident> listForLink(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = loadTenantLink(linkId);
        boolean admin = isAdmin(actor);
        if (!admin
                && !participantPort.findParticipantUserIds(link).contains(actor.userId())
                && !canEditLinkedDossier(link, actor)) {
            throw new AccessDeniedException("User is not a workflow participant nor a dossier editor");
        }
        if (link.processInstanceId() == null) {
            return List.of();
        }
        List<WorkflowIncident> found = incidentPort.listByProcessInstance(link.processInstanceId(), tenantId);
        return admin ? found : found.stream().map(WorkflowIncident::withoutMessage).toList();
    }

    @Override
    public void retry(UUID linkId, String jobId, AccessContext.User actor) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Retrying an incident requires TENANT_ADMIN or WORKFLOW_ADMIN");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        DossierWorkflowLink link = loadTenantLink(linkId);
        WorkflowIncident incident = incidentPort.find(jobId, tenantId)
                .filter(found -> found.processInstanceId().equals(link.processInstanceId()))
                .orElseThrow(() -> new WorkflowIncidentNotFoundException(
                        "No failed job " + jobId + " in workflow link " + linkId));
        if (incident.state() != IncidentState.INCIDENT) {
            throw new IncidentNotRetryableException("Job " + jobId + " still has automatic retries left ("
                    + incident.retriesLeft() + "); only a dead-letter incident can be retried");
        }
        incidentPort.retry(jobId, tenantId, RETRIES_ON_RETRY);
    }

    @Override
    public TenantIncidentPage listForTenant(AccessContext.User actor, PageQuery query) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Listing incidents requires TENANT_ADMIN or WORKFLOW_ADMIN");
        }
        List<WorkflowIncident> scanned = incidentPort.listByTenant(tenantId, TENANT_SCAN_LIMIT);
        Map<String, Optional<DossierWorkflowLink>> linksByProcess = new HashMap<>();
        Map<UUID, Optional<Dossier>> dossiersById = new HashMap<>();
        List<TenantIncident> attached = new ArrayList<>();
        for (WorkflowIncident incident : scanned) {
            Optional<DossierWorkflowLink> link = linksByProcess.computeIfAbsent(incident.processInstanceId(),
                    pi -> linkRepository.findByProcessInstanceId(tenantId, pi));
            if (link.isEmpty()) {
                continue; // process sans dossier : hors périmètre
            }
            Optional<Dossier> dossier = dossiersById.computeIfAbsent(link.get().dossierId(),
                    id -> dossierRepository.findById(id).filter(d -> tenantId.equals(d.tenantId())));
            attached.add(new TenantIncident(incident, link.get().id(), link.get().dossierId(),
                    dossier.map(Dossier::reference).orElse(null), link.get().processDefinitionKey()));
        }
        int from = Math.min(query.page() * query.size(), attached.size());
        int to = Math.min(from + query.size(), attached.size());
        int totalPages = (attached.size() + query.size() - 1) / query.size();
        PageResult<TenantIncident> page = new PageResult<>(
                attached.subList(from, to), query.page(), query.size(), attached.size(), totalPages);
        return new TenantIncidentPage(page, scanned.size() >= TENANT_SCAN_LIMIT);
    }

    @Override
    public List<WorkflowIncident> listForDossierSystem(UUID dossierId) {
        Objects.requireNonNull(dossierId, "dossierId");
        List<WorkflowIncident> result = new ArrayList<>();
        for (DossierWorkflowLink link : linkRepository.findByDossierIdOrderBySequence(dossierId)) {
            if (link.processInstanceId() != null && tenantId.equals(link.tenantId())) {
                incidentPort.listByProcessInstance(link.processInstanceId(), tenantId).stream()
                        .map(WorkflowIncident::withoutMessage)
                        .forEach(result::add);
            }
        }
        return List.copyOf(result);
    }

    private DossierWorkflowLink loadTenantLink(UUID linkId) {
        return linkRepository.findById(linkId)
                .filter(link -> tenantId.equals(link.tenantId()))
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
    }

    private boolean canEditLinkedDossier(DossierWorkflowLink link, AccessContext.User actor) {
        return dossierRepository.findById(link.dossierId())
                .filter(dossier -> tenantId.equals(dossier.tenantId()))
                .map(dossier -> dossierAccessPolicy.canEdit(dossier, actor))
                .orElse(false);
    }

    private static boolean isAdmin(AccessContext.User actor) {
        return actor.hasRole("TENANT_ADMIN") || actor.hasRole("WORKFLOW_ADMIN");
    }
}
