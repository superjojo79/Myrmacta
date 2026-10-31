package be.myrmacta.domain.workflow.usecase;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.reporting.model.DossierSlaRow;
import be.myrmacta.domain.reporting.model.SlaReportAssembler;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.MyWorkflowTask;
import be.myrmacta.domain.workflow.port.in.ListMyTasksUseCase;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListMyTasksUseCaseImpl implements ListMyTasksUseCase {

    private final String tenantId;
    private final WorkflowTaskInboxPort inboxPort;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final DossierTypeRepository typeRepository;
    private final Clock clock;
    private final UserGroupsUseCase groups;
    private final DossierAccessPolicy accessPolicy;

    public ListMyTasksUseCaseImpl(
            String tenantId,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            Clock clock) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.inboxPort = Objects.requireNonNull(inboxPort, "inboxPort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.groups = null;
        this.accessPolicy = null;
    }

    /** TODO §113 (D-GROUP-ASSIGN) — avec les groupes de l'utilisateur et la vérification d'édition des dossiers. */
    public ListMyTasksUseCaseImpl(
            String tenantId,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            DossierTypeRepository typeRepository,
            Clock clock,
            UserGroupsUseCase groups,
            DossierAccessPolicy accessPolicy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.inboxPort = Objects.requireNonNull(inboxPort, "inboxPort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.groups = Objects.requireNonNull(groups, "groups");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
    }

    /**
     * Les tâches orphelines (variable dossierId absente/invalide, lien ou dossier introuvable) sont
     * filtrées après pagination Flowable — {@code content()} peut donc contenir moins d'éléments que
     * {@code size} même si {@code totalElements} l'indique, sans que cela ne signale une page invalide.
     */
    @Override
    public PageResult<MyWorkflowTask> listMine(String assigneeId, PageQuery query) {
        Objects.requireNonNull(assigneeId, "assigneeId");
        Objects.requireNonNull(query, "query");
        if (assigneeId.isBlank()) {
            throw new IllegalArgumentException("assigneeId is required");
        }
        return enrich(inboxPort.findActiveAssignedTo(assigneeId, query), null);
    }

    /**
     * Une tâche libre d'un groupe n'apparaît que si l'utilisateur peut modifier son dossier : sinon il ne pourrait ni la prendre
     * ni la terminer, et sa référence ne le regarde pas. Même filtrage après pagination que pour les tâches orphelines.
     */
    @Override
    public PageResult<MyWorkflowTask> listMine(AccessContext.User actor, PageQuery query) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(query, "query");
        if (groups == null) {
            return listMine(actor.userId(), query);
        }
        return enrich(inboxPort.findActiveForUser(actor.userId(), groups.groupsOf(actor.userId()), query), actor);
    }

    private PageResult<MyWorkflowTask> enrich(PageResult<WorkflowTaskInboxPort.ActiveTaskSnapshot> page, AccessContext.User actor) {
        List<MyWorkflowTask> result = new ArrayList<>();
        for (WorkflowTaskInboxPort.ActiveTaskSnapshot task : page.content()) {
            var dossierIdText = workflowEnginePort.findDossierIdVariable(task.processInstanceId());
            if (dossierIdText.isEmpty()) {
                continue;
            }
            UUID dossierId;
            try {
                dossierId = UUID.fromString(dossierIdText.get());
            } catch (IllegalArgumentException ex) {
                continue;
            }
            var link = linkRepository.findByProcessInstanceId(tenantId, task.processInstanceId());
            if (link.isEmpty()) {
                continue;
            }
            var dossier = dossierRepository.findById(dossierId);
            if (dossier.isEmpty()) {
                continue;
            }
            var loaded = dossier.get();
            if (actor != null && task.assigneeId() == null && !accessPolicy.canEdit(loaded, actor)) {
                continue;
            }
            Integer slaHours = typeRepository
                    .findByCode(tenantId, loaded.dossierTypeCode())
                    .map(type -> type.slaTargetHours())
                    .orElse(null);
            DossierSlaRow slaRow = new DossierSlaRow(
                    loaded.id(),
                    loaded.dossierTypeCode(),
                    loaded.createdAt(),
                    loaded.closedAt(),
                    loaded.updatedAt(),
                    loaded.status());
            result.add(new MyWorkflowTask(
                    task.taskId(),
                    task.name(),
                    task.assigneeId(),
                    task.createdAt(),
                    task.processInstanceId(),
                    dossierId,
                    loaded.reference(),
                    link.get().id(),
                    SlaReportAssembler.remainingHoursFor(slaRow, slaHours, clock.instant()),
                    task.formKey()));
        }
        return PageResult.of(List.copyOf(result), page.page(), page.size(), page.totalElements());
    }
}
