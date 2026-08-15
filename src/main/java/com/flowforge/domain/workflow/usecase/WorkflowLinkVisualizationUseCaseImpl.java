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
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowLinkDossierView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceMissingException;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import com.flowforge.domain.workflow.port.in.GetWorkflowDiagramUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowHistoryUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowLinkDossierUseCase;
import com.flowforge.domain.workflow.port.in.GetWorkflowTasksUseCase;
import com.flowforge.domain.workflow.port.in.ReassignWorkflowTaskUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class WorkflowLinkVisualizationUseCaseImpl implements
        GetWorkflowDiagramUseCase,
        GetWorkflowHistoryUseCase,
        GetWorkflowTasksUseCase,
        GetWorkflowLinkDossierUseCase,
        ReassignWorkflowTaskUseCase {

    private final String tenantId;
    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository dossierTypeRepository;
    private final DossierElementRepository elementRepository;
    private final ListDossierRelationsUseCase listDossierRelationsUseCase;
    private final WorkflowVisualizationPort visualizationPort;
    private final WorkflowParticipantPort participantPort;
    private final DossierAccessPolicy dossierAccessPolicy;

    public WorkflowLinkVisualizationUseCaseImpl(
            String tenantId,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository dossierTypeRepository,
            DossierElementRepository elementRepository,
            ListDossierRelationsUseCase listDossierRelationsUseCase,
            WorkflowVisualizationPort visualizationPort,
            WorkflowParticipantPort participantPort,
            DossierAccessPolicy dossierAccessPolicy) {
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
    }

    @Override
    public WorkflowDiagramView getDiagram(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        return visualizationPort.getDiagram(requireProcessInstance(link), tenantId);
    }

    @Override
    public List<ActivityHistoryEntry> getHistory(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        return visualizationPort.getHistory(requireProcessInstance(link), tenantId);
    }

    @Override
    public List<WorkflowTaskView> getTasks(UUID linkId, AccessContext.User actor) {
        DossierWorkflowLink link = requireReadableLink(linkId, actor);
        return visualizationPort.listActiveTasks(requireProcessInstance(link), tenantId);
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
        // Best-effort guard against typos, not an identity check (no local user directory —
        // OIDC only): assigneeId must already be a recognized participant or configured observer.
        if (!participantPort.findParticipantUserIds(link).contains(assigneeId)) {
            throw new IllegalArgumentException(
                    "assigneeId is not a known participant or observer of this workflow: " + assigneeId);
        }
        visualizationPort.reassignTask(processInstanceId, taskId, assigneeId, tenantId);
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
