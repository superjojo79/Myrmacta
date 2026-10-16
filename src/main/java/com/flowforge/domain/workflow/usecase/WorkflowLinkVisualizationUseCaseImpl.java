package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.DossierFieldRedactor;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.user.model.KnownUser;
import com.flowforge.domain.user.port.out.KnownUserRepository;
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowLinkDossierView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceMissingException;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import com.flowforge.domain.workflow.port.in.ClaimWorkflowTaskUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowDiagramUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowHistoryUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowLinkDossierUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowTasksUseCase;
import com.flowforge.domain.workflow.port.in.ListAssignableUsersUseCase;
import com.flowforge.domain.workflow.port.in.ReassignWorkflowTaskUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class WorkflowLinkVisualizationUseCaseImpl implements
        GetWorkflowDiagramUseCase,
        GetWorkflowHistoryUseCase,
        GetWorkflowTasksUseCase,
        GetWorkflowLinkDossierUseCase,
        ReassignWorkflowTaskUseCase,
        ClaimWorkflowTaskUseCase,
        ListAssignableUsersUseCase {

    private final String tenantId;
    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository dossierTypeRepository;
    private final DossierElementRepository elementRepository;
    private final ListDossierRelationsUseCase listDossierRelationsUseCase;
    private final WorkflowVisualizationPort visualizationPort;
    private final WorkflowParticipantPort participantPort;
    private final DossierAccessPolicy dossierAccessPolicy;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final KnownUserRepository knownUserRepository;

    public WorkflowLinkVisualizationUseCaseImpl(
            String tenantId,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierElementRepository elementRepository,
            ListDossierRelationsUseCase listDossierRelationsUseCase,
            WorkflowVisualizationPort visualizationPort,
            WorkflowParticipantPort participantPort,
            DossierAccessPolicy dossierAccessPolicy,
            SendNotificationUseCase sendNotificationUseCase,
            KnownUserRepository knownUserRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.dossierTypeRepository = Objects.requireNonNull(dossierTypeRepository, "dossierTypeRepository");
        this.elementRepository = Objects.requireNonNull(elementRepository, "elementRepository");
        this.listDossierRelationsUseCase =
                Objects.requireNonNull(listDossierRelationsUseCase, "listDossierRelationsUseCase");
        this.visualizationPort = Objects.requireNonNull(visualizationPort, "visualizationPort");
        this.participantPort = Objects.requireNonNull(participantPort, "participantPort");
        this.dossierAccessPolicy = Objects.requireNonNull(dossierAccessPolicy, "dossierAccessPolicy");
        this.sendNotificationUseCase = Objects.requireNonNull(sendNotificationUseCase, "sendNotificationUseCase");
        this.knownUserRepository = Objects.requireNonNull(knownUserRepository, "knownUserRepository");
    }

    @Override
    public WorkflowDiagramView getDiagram(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        return visualizationPort.getDiagram(requireProcessInstance(link), tenantId);
    }

    @Override
    public PageResult<ActivityHistoryEntry> getHistory(UUID linkId, AccessContext.User actor, PageQuery query) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        return visualizationPort.getHistory(requireProcessInstance(link), tenantId, query);
    }

    @Override
    public PageResult<WorkflowTaskView> getTasks(UUID linkId, AccessContext.User actor, PageQuery query) {
        // D-ASSIGN-CLAIM : un éditeur du dossier voit les tâches même sans y avoir encore participé,
        // sinon il ne pourrait jamais prendre une tâche libre. Diagramme et historique restent
        // réservés aux participants.
        DossierWorkflowLink link = loadTenantLink(linkId);
        if (!isAdmin(actor)
                && !participantPort.findParticipantUserIds(link).contains(actor.userId())
                && !canEditLinkedDossier(link, actor)) {
            throw new AccessDeniedException("User is not a workflow participant nor a dossier editor");
        }
        return visualizationPort.listActiveTasks(requireProcessInstance(link), tenantId, query);
    }

    @Override
    public WorkflowLinkDossierView getDossier(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        Dossier dossier = dossierRepository.findById(link.dossierId())
                .filter(candidate -> tenantId.equals(candidate.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + link.dossierId()));
        dossierAccessPolicy.assertCanView(dossier, actor);
        var elements = elementRepository.findByDossierIdOrderByAddedAtAsc(link.dossierId());
        var relations = listDossierRelationsUseCase.list(link.dossierId(), actor);
        return new WorkflowLinkDossierView(redactSensitiveFields(dossier, actor), elements, relations);
    }

    private Dossier redactSensitiveFields(Dossier dossier, AccessContext.User actor) {
        List<FieldDefinition> fieldDefinitions = dossierTypeRepository
                .findByCode(dossier.tenantId(), dossier.dossierTypeCode())
                .map(DossierType::fieldDefinitions)
                .orElseGet(List::of);
        return DossierFieldRedactor.redact(dossier, fieldDefinitions, actor);
    }

    @Override
    public void reassign(UUID linkId, String taskId, String assigneeId, AccessContext.User actor) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Workflow task reassignment requires TENANT_ADMIN or WORKFLOW_ADMIN");
        }
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        if (assigneeId == null || assigneeId.isBlank()) {
            throw new IllegalArgumentException("assigneeId is required");
        }
        DossierWorkflowLink link = loadTenantLink(linkId);
        String processInstanceId = requireProcessInstance(link);
        // Guard against typos: assigneeId must be a recognized participant or configured observer,
        // or a user of the local directory (D-ASSIGN-DIR) who can edit the dossier — assigning a
        // task to someone who could not complete it would only strand it.
        if (!participantPort.findParticipantUserIds(link).contains(assigneeId)
                && !isAssignableKnownUser(link, assigneeId)) {
            throw new IllegalArgumentException(
                    "assigneeId is neither a participant of this workflow nor a known user who can edit the dossier: "
                            + assigneeId);
        }
        visualizationPort.reassignTask(processInstanceId, taskId, assigneeId, tenantId);
        // M5 (TODO §66), transactional semantics fixed per F4 (Phase 10 adversarial review): not
        // wrapped in try/catch — unlike WorkflowStateEventListener (an async engine-event reaction
        // outside the request), this runs inside the synchronous admin request, and
        // WorkflowLinkVisualizationController#reassign is now @Transactional so a send() failure
        // rolls back the reassignTask(...) call above together with it (Flowable's TaskService
        // participates in the ambient Spring transaction) instead of leaving the reassignment
        // committed while the caller sees a 500. Propagating the failure here (rather than
        // swallowing it) is consistent with every other write use case in this bounded context
        // (domain must not depend on org.slf4j — ArchUnit — so silent swallow-without-logging is
        // not an option here the way it is in an infrastructure listener).
        // Known overlap, not a bug: Flowable's taskService.setAssignee (called by
        // visualizationPort.reassignTask) also fires TASK_ASSIGNED, which
        // WorkflowStateEventListener already turns into a generic WORKFLOW_STATE_CHANGED
        // broadcast to *all* participants — the new assignee receives both that broadcast and
        // this targeted TASK_REASSIGNED. Left as-is (documented, not silently accepted).
        sendNotificationUseCase.send(
                new NotificationEvent(
                        List.of(NotificationChannel.BROWSER),
                        null,
                        null,
                        null,
                        assigneeId,
                        null,
                        Map.of(
                                "type", "TASK_REASSIGNED",
                                "title", "Tâche réassignée",
                                "body", "Une tâche du workflow vous a été réassignée.",
                                "linkId", link.id().toString(),
                                "dossierId", link.dossierId().toString(),
                                "taskId", taskId)),
                tenantId);
    }

    @Override
    public void claim(UUID linkId, String taskId, AccessContext.User actor) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        DossierWorkflowLink link = loadTenantLink(linkId);
        String processInstanceId = requireProcessInstance(link);
        dossierAccessPolicy.assertCanEdit(loadLinkedDossier(link), actor);
        // Pas de notification : l'acteur s'assigne lui-même.
        visualizationPort.claimTask(processInstanceId, taskId, actor.userId(), tenantId);
    }

    @Override
    public List<KnownUser> listAssignableUsers(UUID linkId, AccessContext.User actor) {
        if (!isAdmin(actor)) {
            throw new AccessDeniedException("Listing assignable users requires TENANT_ADMIN or WORKFLOW_ADMIN");
        }
        Dossier dossier = loadLinkedDossier(loadTenantLink(linkId));
        return knownUserRepository.findAll(tenantId).stream()
                .filter(user -> canEdit(dossier, user))
                .toList();
    }

    private boolean isAssignableKnownUser(DossierWorkflowLink link, String userId) {
        return knownUserRepository.findById(tenantId, userId)
                .filter(user -> canEdit(loadLinkedDossier(link), user))
                .isPresent();
    }

    private boolean canEdit(Dossier dossier, KnownUser user) {
        return dossierAccessPolicy.canEdit(dossier, AccessContext.user(user.userId(), user.roles()));
    }

    private boolean canEditLinkedDossier(DossierWorkflowLink link, AccessContext.User actor) {
        return dossierRepository.findById(link.dossierId())
                .filter(dossier -> tenantId.equals(dossier.tenantId()))
                .map(dossier -> dossierAccessPolicy.canEdit(dossier, actor))
                .orElse(false);
    }

    private Dossier loadLinkedDossier(DossierWorkflowLink link) {
        return dossierRepository.findById(link.dossierId())
                .filter(dossier -> tenantId.equals(dossier.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + link.dossierId()));
    }

    private DossierWorkflowLink requireReadableLink(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = loadTenantLink(linkId);
        if (!isAdmin(actor) && !participantPort.findParticipantUserIds(link).contains(actor.userId())) {
            throw new AccessDeniedException("User is not a workflow participant");
        }
        return link;
    }

    private DossierWorkflowLink loadTenantLink(UUID linkId) {
        return linkRepository.findById(linkId)
                .filter(link -> tenantId.equals(link.tenantId()))
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
    }

    private static boolean isAdmin(AccessContext.User actor) {
        return actor.hasRole("TENANT_ADMIN") || actor.hasRole("WORKFLOW_ADMIN");
    }

    private static String requireProcessInstance(DossierWorkflowLink link) {
        if (link.processInstanceId() == null || link.processInstanceId().isBlank()) {
            throw new WorkflowProcessInstanceMissingException(
                    "Workflow link has no process instance: " + link.id());
        }
        return link.processInstanceId();
    }
}
