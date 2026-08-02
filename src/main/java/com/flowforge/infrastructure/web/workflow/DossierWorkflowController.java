package com.flowforge.infrastructure.web.workflow;

import com.flowforge.application.workflow.ManualStartOrchestrator;
import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossiers/{id}/workflows")
class DossierWorkflowController {

    private final DossierRepository dossierRepository;
    private final DossierAccessPolicy accessPolicy;
    private final ManualStartOrchestrator orchestrator;
    private final GetDossierWorkflowHistoryUseCase historyUseCase;
    private final AccessContextFactory accessContextFactory;

    DossierWorkflowController(
            DossierRepository dossierRepository,
            DossierAccessPolicy accessPolicy,
            ManualStartOrchestrator orchestrator,
            GetDossierWorkflowHistoryUseCase historyUseCase,
            AccessContextFactory accessContextFactory) {
        this.dossierRepository = dossierRepository;
        this.accessPolicy = accessPolicy;
        this.orchestrator = orchestrator;
        this.historyUseCase = historyUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    ResponseEntity<WorkflowLinkResponse> start(
            @PathVariable("id") UUID dossierId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody StartWorkflowRequest request,
            Authentication authentication) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        AccessContext.User actor = accessContextFactory.user(authentication);
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanEdit(dossier, actor);
        var result = orchestrator.start(
                dossierId,
                required(request.processDefinitionKey(), "processDefinitionKey"),
                request.variables() == null ? Map.of() : request.variables(),
                idempotencyKey);
        var link = result.link();
        int status = result.replay() ? 200 : 201;
        return ResponseEntity.status(status)
                .location(URI.create("/api/dossiers/" + dossierId + "/workflows/" + link.id()))
                .body(WorkflowLinkResponse.from(link));
    }

    @GetMapping
    List<WorkflowLinkResponse> history(@PathVariable("id") UUID dossierId, Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new DossierNotFoundException("Dossier not found: " + dossierId));
        accessPolicy.assertCanView(dossier, actor);
        return historyUseCase.getHistory(dossierId).stream().map(WorkflowLinkResponse::from).toList();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
