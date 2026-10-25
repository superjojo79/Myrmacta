package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.IncidentNotRetryableException;
import be.myrmacta.domain.workflow.model.IncidentState;
import be.myrmacta.domain.workflow.model.TenantIncident;
import be.myrmacta.domain.workflow.model.WorkflowIncident;
import be.myrmacta.domain.workflow.model.WorkflowIncidentNotFoundException;
import be.myrmacta.domain.workflow.port.in.ListDossierIncidentsSystemUseCase;
import be.myrmacta.domain.workflow.port.in.WorkflowIncidentsUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowIncidentPort;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
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
public final class WorkflowIncidentsUseCaseImpl implements WorkflowIncidentsUseCase, ListDossierIncidentsSystemUseCase {

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
            // Revue Info : un lecteur du dossier ouvre la fiche sans déclencher de refus d'audit ; il ne voit rien.
            if (canViewLinkedDossier(link, actor)) {
                return List.of();
            }
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
            // Revue L2 : cas typique d'un double clic — message neutre, sans détail technique.
            throw new IncidentNotRetryableException("Incident " + jobId + " has already been retried or is retrying");
        }
        incidentPort.retry(jobId, tenantId, RETRIES_ON_RETRY);
    }

    @Override
    public TenantIncidentPage listForTenant(AccessContext.User actor, PageQuery query) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Listing incidents requires TENANT_ADMIN or WORKFLOW_ADMIN");
        }
        // Revue L4 : lire un élément de plus que la borne pour savoir si elle est réellement dépassée.
        List<WorkflowIncident> scanned = incidentPort.listByTenant(tenantId, TENANT_SCAN_LIMIT + 1);
        boolean truncated = scanned.size() > TENANT_SCAN_LIMIT;
        List<TenantIncident> attached = attach(truncated ? scanned.subList(0, TENANT_SCAN_LIMIT) : scanned);
        return new TenantIncidentPage(PageResult.slice(attached, query), truncated);
    }

    @Override
    public List<TenantIncident> listForDossierSystem(UUID dossierId) {
        Objects.requireNonNull(dossierId, "dossierId");
        List<TenantIncident> result = new ArrayList<>();
        for (DossierWorkflowLink link : linkRepository.findByDossierIdOrderBySequence(dossierId)) {
            if (link.processInstanceId() != null && tenantId.equals(link.tenantId())) {
                incidentPort.listByProcessInstance(link.processInstanceId(), tenantId).stream()
                        .map(incident -> new TenantIncident(incident.withoutMessage(), link.id(), link.dossierId(), null,
                                link.processDefinitionKey()))
                        .forEach(result::add);
            }
        }
        return List.copyOf(result);
    }

    /** Rattache chaque échec à son lien et à son dossier, en une lecture de liens ; les process sans dossier sont ignorés. */
    private List<TenantIncident> attach(List<WorkflowIncident> incidents) {
        Map<String, DossierWorkflowLink> linksByProcess = new HashMap<>();
        linkRepository.findByProcessInstanceIds(tenantId,
                        incidents.stream().map(WorkflowIncident::processInstanceId).distinct().toList())
                .forEach(link -> linksByProcess.put(link.processInstanceId(), link));
        Map<UUID, Optional<Dossier>> dossiersById = new HashMap<>();
        List<TenantIncident> attached = new ArrayList<>();
        for (WorkflowIncident incident : incidents) {
            DossierWorkflowLink link = linksByProcess.get(incident.processInstanceId());
            if (link == null) {
                continue; // process sans dossier : hors périmètre
            }
            Optional<Dossier> dossier = dossiersById.computeIfAbsent(link.dossierId(),
                    id -> dossierRepository.findById(id).filter(d -> tenantId.equals(d.tenantId())));
            attached.add(new TenantIncident(incident, link.id(), link.dossierId(),
                    dossier.map(Dossier::reference).orElse(null), link.processDefinitionKey()));
        }
        return attached;
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

    private boolean canViewLinkedDossier(DossierWorkflowLink link, AccessContext.User actor) {
        return dossierRepository.findById(link.dossierId())
                .filter(dossier -> tenantId.equals(dossier.tenantId()))
                .map(dossier -> dossierAccessPolicy.canViewType(dossier.tenantId(), dossier.dossierTypeCode(), actor))
                .orElse(false);
    }

    private static boolean isAdmin(AccessContext.User actor) {
        return actor.hasRole("TENANT_ADMIN") || actor.hasRole("WORKFLOW_ADMIN");
    }
}
