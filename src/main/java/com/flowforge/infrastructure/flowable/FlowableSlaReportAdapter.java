package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.reporting.model.ActiveTaskRow;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.HistoricStepRow;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.task.api.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FlowableSlaReportAdapter {

    private static final Logger log = LoggerFactory.getLogger(FlowableSlaReportAdapter.class);

    private final TaskService taskService;
    private final HistoryService historyService;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final DossierWorkflowLinkRepository linkRepository;

    public FlowableSlaReportAdapter(
            TaskService taskService,
            HistoryService historyService,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierWorkflowLinkRepository linkRepository) {
        this.taskService = taskService;
        this.historyService = historyService;
        this.workflowEnginePort = workflowEnginePort;
        this.dossierRepository = dossierRepository;
        this.linkRepository = linkRepository;
    }

    public List<ActiveTaskRow> findActiveTasks(String tenantId, Set<String> allowedTypeCodes) {
        try {
            List<Task> tasks = taskService.createTaskQuery().active().list();
            List<ActiveTaskRow> rows = new ArrayList<>();
            for (Task task : tasks) {
                Optional<String> dossierIdText = workflowEnginePort.findDossierIdVariable(task.getProcessInstanceId());
                if (dossierIdText.isEmpty()) {
                    continue;
                }
                UUID dossierId;
                try {
                    dossierId = UUID.fromString(dossierIdText.get());
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                Optional<Dossier> dossier = dossierRepository.findById(dossierId);
                if (dossier.isEmpty()) {
                    continue;
                }
                Dossier loaded = dossier.get();
                if (!tenantId.equals(loaded.tenantId())) {
                    continue;
                }
                if (allowedTypeCodes != null && !allowedTypeCodes.contains(loaded.dossierTypeCode())) {
                    continue;
                }
                DossierSlaRow slaRow = new DossierSlaRow(
                        loaded.id(),
                        loaded.dossierTypeCode(),
                        loaded.createdAt(),
                        loaded.closedAt(),
                        loaded.updatedAt(),
                        loaded.status());
                rows.add(new ActiveTaskRow(loaded.id(), task.getAssignee(), loaded.dossierTypeCode(), slaRow));
            }
            return List.copyOf(rows);
        } catch (RuntimeException ex) {
            log.warn("sla.flowable.degraded active-tasks: {}", ex.getMessage());
            return List.of();
        }
    }

    public List<HistoricStepRow> findCompletedUserTaskDurations(Set<UUID> dossierIds) {
        if (dossierIds == null || dossierIds.isEmpty()) {
            return List.of();
        }
        try {
            Set<String> processInstanceIds = dossierIds.stream()
                    .flatMap(id -> linkRepository.findByDossierIdOrderBySequence(id).stream())
                    .map(link -> link.processInstanceId())
                    .filter(id -> id != null && !id.isBlank())
                    .collect(java.util.stream.Collectors.toSet());
            if (processInstanceIds.isEmpty()) {
                return List.of();
            }
            List<HistoricActivityInstance> activities = historyService
                    .createHistoricActivityInstanceQuery()
                    .activityType("userTask")
                    .finished()
                    .list();
            List<HistoricStepRow> rows = new ArrayList<>();
            for (HistoricActivityInstance activity : activities) {
                if (activity.getProcessInstanceId() == null
                        || !processInstanceIds.contains(activity.getProcessInstanceId())) {
                    continue;
                }
                Long duration = activity.getDurationInMillis();
                if (duration == null) {
                    continue;
                }
                String name = activity.getActivityName();
                if (name == null || name.isBlank()) {
                    name = activity.getActivityId();
                }
                if (name == null || name.isBlank()) {
                    continue;
                }
                rows.add(new HistoricStepRow(name, duration));
            }
            return List.copyOf(rows);
        } catch (RuntimeException ex) {
            log.warn("sla.flowable.degraded historic-steps: {}", ex.getMessage());
            return List.of();
        }
    }
}
