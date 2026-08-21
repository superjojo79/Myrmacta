package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.MyWorkflowTask;
import com.flowforge.domain.workflow.port.in.ListMyTasksUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
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

    public ListMyTasksUseCaseImpl(
            String tenantId,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.inboxPort = Objects.requireNonNull(inboxPort, "inboxPort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
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
        PageResult<WorkflowTaskInboxPort.ActiveTaskSnapshot> page =
                inboxPort.findActiveAssignedTo(assigneeId, query);
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
            result.add(new MyWorkflowTask(
                    task.taskId(),
                    task.name(),
                    task.assigneeId(),
                    task.createdAt(),
                    task.processInstanceId(),
                    dossierId,
                    dossier.get().reference(),
                    link.get().id()));
        }
        return PageResult.of(List.copyOf(result), page.page(), page.size(), page.totalElements());
    }
}
