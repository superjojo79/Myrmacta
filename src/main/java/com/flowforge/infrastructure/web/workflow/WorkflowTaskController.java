package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.workflow.port.in.CompleteTaskUseCase;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-tasks")
class WorkflowTaskController {

    private final CompleteTaskUseCase completeTaskUseCase;
    private final WorkflowEnginePort workflowEnginePort;
    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final AccessContextFactory accessContextFactory;

    WorkflowTaskController(
            CompleteTaskUseCase completeTaskUseCase,
            WorkflowEnginePort workflowEnginePort,
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            AccessContextFactory accessContextFactory) {
        this.completeTaskUseCase = completeTaskUseCase;
        this.workflowEnginePort = workflowEnginePort;
        this.dossierRepository = dossierRepository;
        this.accessPolicy = accessPolicy;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping("/{taskId}/complete")
    ResponseEntity<Void> complete(
            @PathVariable String taskId,
            @RequestBody(required = false) CompleteTaskRequest request,
            Authentication authentication) {
        String processInstanceId = workflowEnginePort.findProcessInstanceIdByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Workflow task not found: " + taskId));
        String dossierId = workflowEnginePort.findDossierIdVariable(processInstanceId)
                .orElseThrow(() -> new IllegalArgumentException("Task is not linked to a dossier"));
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanEdit(dossier, accessContextFactory.user(authentication));
        completeTaskUseCase.complete(taskId, request == null || request.variables() == null ? Map.of() : request.variables());
        return ResponseEntity.noContent().build();
    }
}
