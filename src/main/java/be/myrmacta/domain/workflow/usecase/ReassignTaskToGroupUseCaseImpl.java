package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.TaskGroupReassignment;
import be.myrmacta.domain.workflow.model.WorkflowProcessInstanceMissingException;
import be.myrmacta.domain.workflow.port.in.ReassignTaskToGroupUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ReassignTaskToGroupUseCaseImpl implements ReassignTaskToGroupUseCase {

    private final String tenantId;
    private final DossierWorkflowLinkRepository links;
    private final DossierRepository dossiers;
    private final DossierAccessPolicy accessPolicy;
    private final WorkflowVisualizationPort engine;
    private final UserGroupsUseCase groups;
    private final WorkflowLinkGroupRepository linkGroups;
    private final DossierElementRepository elements;
    private final Clock clock;

    public ReassignTaskToGroupUseCaseImpl(
            String tenantId,
            DossierWorkflowLinkRepository links,
            DossierRepository dossiers,
            DossierAccessPolicy accessPolicy,
            WorkflowVisualizationPort engine,
            UserGroupsUseCase groups,
            WorkflowLinkGroupRepository linkGroups,
            DossierElementRepository elements,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.links = Objects.requireNonNull(links, "links");
        this.dossiers = Objects.requireNonNull(dossiers, "dossiers");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.engine = Objects.requireNonNull(engine, "engine");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
        this.elements = Objects.requireNonNull(elements, "elements");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Toutes les vérifications avant la première écriture ; l'appelant porte la transaction (moteur, lien, note ensemble). */
    @Override
    public void reassign(UUID linkId, String taskId, String groupCode, AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId is required");
        }
        DossierWorkflowLink link = links.findById(linkId)
                .filter(found -> tenantId.equals(found.tenantId()))
                .orElseThrow(() -> new DossierWorkflowLinkNotFoundException("Workflow link not found: " + linkId));
        if (link.processInstanceId() == null || link.processInstanceId().isBlank()) {
            throw new WorkflowProcessInstanceMissingException("Workflow link has no process instance: " + link.id());
        }
        Dossier dossier = dossiers.findById(link.dossierId())
                .filter(found -> tenantId.equals(found.tenantId()))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + link.dossierId()));
        accessPolicy.assertCanEdit(dossier, actor);
        String group = knownGroup(groupCode);

        TaskGroupReassignment before = engine.reassignTaskToGroup(link.processInstanceId(), taskId, group, tenantId);
        linkGroups.assign(tenantId, link.id(), group);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("event", "TASK_GROUP_REASSIGNED");
        metadata.put("taskId", taskId);
        metadata.put("taskName", before.taskName());
        metadata.put("fromGroups", before.previousGroups());
        if (before.previousAssignee() != null) {
            metadata.put("fromAssignee", before.previousAssignee());
        }
        metadata.put("toGroup", group);
        elements.save(new DossierElement(UUID.randomUUID(), dossier.id(), tenantId, ElementType.NOTE, label(before, group), null, metadata,
                actor.userId(), clock.instant(), null));
    }

    private String knownGroup(String groupCode) {
        if (groupCode == null || groupCode.isBlank()) {
            throw new IllegalArgumentException("groupCode is required");
        }
        try {
            return groups.get(groupCode.strip()).code();
        } catch (UserGroupNotFoundException unknown) {
            throw new IllegalArgumentException("Unknown user group: " + groupCode.strip());
        }
    }

    private static String label(TaskGroupReassignment before, String group) {
        String from = before.previousGroups().isEmpty() ? "(aucun groupe)" : String.join(", ", before.previousGroups());
        if (before.previousAssignee() != null) {
            from = from + " (" + before.previousAssignee() + ")";
        }
        return "Tâche « " + before.taskName() + " » réaffectée de " + from + " à " + group;
    }
}
