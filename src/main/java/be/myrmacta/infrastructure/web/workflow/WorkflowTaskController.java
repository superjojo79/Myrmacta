package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.MyWorkflowTask;
import be.myrmacta.domain.workflow.model.WorkflowTaskNotFoundException;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.in.ListMyTasksUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import be.myrmacta.infrastructure.web.dossier.DossierAccessGuard;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-tasks")
class WorkflowTaskController {

    private final CompleteTaskUseCase completeTaskUseCase;
    private final ListMyTasksUseCase listMyTasksUseCase;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierAccessGuard accessGuard;
    private final AccessContextFactory accessContextFactory;

    WorkflowTaskController(
            CompleteTaskUseCase completeTaskUseCase,
            ListMyTasksUseCase listMyTasksUseCase,
            WorkflowEnginePort workflowEnginePort,
            DossierAccessGuard accessGuard,
            AccessContextFactory accessContextFactory) {
        this.completeTaskUseCase = completeTaskUseCase;
        this.listMyTasksUseCase = listMyTasksUseCase;
        this.workflowEnginePort = workflowEnginePort;
        this.accessGuard = accessGuard;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/mine")
    PageResponse<MyWorkflowTaskResponse> mine(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        var actor = accessContextFactory.user(authentication);
        return PageResponse.from(listMyTasksUseCase
                .listMine(actor.userId(), PageQueryParser.parse(page, size, sort, ListSorts.MY_WORKFLOW_TASKS))
                .map(MyWorkflowTaskResponse::from));
    }

    /**
     * {@code @Transactional} here (not on the use case/port, both domain/infra POJOs, same
     * rationale as {@code DossierExportController}) is what lets the {@code TaskQuestionAnswer}
     * write and {@code completeTask(...)} roll back together on failure — Flowable's Spring Boot
     * integration lets {@code TaskService} participate in the ambient transaction (D-USERQ kind
     * {@code user-question}, TODO §55).
     */
    @PostMapping("/{taskId}/complete")
    @Transactional
    ResponseEntity<Void> complete(
            @PathVariable String taskId,
            @RequestBody(required = false) CompleteTaskRequest request,
            Authentication authentication) {
        String processInstanceId = workflowEnginePort.findProcessInstanceIdByTaskId(taskId)
                .orElseThrow(() -> new WorkflowTaskNotFoundException("Workflow task not found: " + taskId));
        String dossierId = workflowEnginePort.findDossierIdVariable(processInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Task is not linked to a dossier"));
        var actor = accessContextFactory.user(authentication);
        accessGuard.requireEditable(UUID.fromString(dossierId), actor);
        completeTaskUseCase.complete(
                taskId,
                dossierId,
                request == null || request.variables() == null ? Map.of() : request.variables(),
                actor);
        return ResponseEntity.noContent().build();
    }

    record MyWorkflowTaskResponse(
            String taskId,
            String name,
            String assigneeId,
            String createdAt,
            String processInstanceId,
            UUID dossierId,
            String dossierReference,
            UUID linkId,
            Double slaRemainingHours,
            String formKey) {

        static MyWorkflowTaskResponse from(MyWorkflowTask task) {
            return new MyWorkflowTaskResponse(
                    task.taskId(),
                    task.name(),
                    task.assigneeId(),
                    task.createdAt() == null ? null : task.createdAt().toString(),
                    task.processInstanceId(),
                    task.dossierId(),
                    task.dossierReference(),
                    task.linkId(),
                    task.slaRemainingHours(),
                    task.formKey());
        }
    }
}
