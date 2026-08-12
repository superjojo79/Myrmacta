package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.MyWorkflowTask;
import com.flowforge.domain.workflow.port.in.ListMyTasksUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListMyTasksUseCaseImpl implements ListMyTasksUseCase {

    public static final int DEFAULT_LIMIT = 100;

    private final String tenantId;
    private final WorkflowTaskInboxPort inboxPort;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierWorkflowLinkRepository linkRepository;
    private final DossierRepository dossierRepository;
    private final int limit;

    public ListMyTasksUseCaseImpl(
            String tenantId,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository) {
        this(tenantId, inboxPort, workflowEnginePort, linkRepository, dossierRepository, DEFAULT_LIMIT);
    }

    public ListMyTasksUseCaseImpl(
            String tenantId,
            WorkflowTaskInboxPort inboxPort,
            WorkflowEnginePort workflowEnginePort,
            DossierWorkflowLinkRepository linkRepository,
            DossierRepository dossierRepository,
            int limit) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.inboxPort = Objects.requireNonNull(inboxPort, "inboxPort");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
        this.linkRepository = Objects.requireNonNull(linkRepository, "linkRepository");
        this.dossierRepository = Objects.requireNonNull(dossierRepository, "dossierRepository");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.limit = limit;
    }

    @Override
    public List<MyWorkflowTask> listMine(String assigneeId) {
        Objects.requireNonNull(assigneeId, "assigneeId");
        if (assigneeId.isBlank()) {
            throw new IllegalArgumentException("assigneeId is required");
        }
        List<MyWorkflowTask> result = new ArrayList<>();
        for (WorkflowTaskInboxPort.ActiveTaskSnapshot task : inboxPort.findActiveAssignedTo(assigneeId, limit)) {
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
        return List.copyOf(result);
    }
}
