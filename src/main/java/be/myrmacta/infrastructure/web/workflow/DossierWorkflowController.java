package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.port.in.GetDossierWorkflowHistoryUseCase;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import be.myrmacta.infrastructure.web.dossier.DossierAccessGuard;
import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dossiers/{id}/workflows")
class DossierWorkflowController {

    private final DossierAccessGuard accessGuard;
    private final ManualStartOrchestrator orchestrator;
    private final GetDossierWorkflowHistoryUseCase historyUseCase;
    private final AccessContextFactory accessContextFactory;

    DossierWorkflowController(
            DossierAccessGuard accessGuard,
            ManualStartOrchestrator orchestrator,
            GetDossierWorkflowHistoryUseCase historyUseCase,
            AccessContextFactory accessContextFactory) {
        this.accessGuard = accessGuard;
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
        accessGuard.requireEditable(dossierId, actor);
        var result = orchestrator.start(
                dossierId,
                required(request.processDefinitionKey(), "processDefinitionKey"),
                request.variables() == null ? Map.of() : request.variables(),
                idempotencyKey,
                actor.userId());
        var link = result.link();
        int status = result.replay() ? 200 : 201;
        return ResponseEntity.status(status)
                .location(URI.create("/api/dossiers/" + dossierId + "/workflows"))
                .body(WorkflowLinkResponse.from(link));
    }

    @GetMapping
    PageResponse<WorkflowLinkResponse> history(
            @PathVariable("id") UUID dossierId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        AccessContext.User actor = accessContextFactory.user(authentication);
        accessGuard.requireViewable(dossierId, actor);
        return PageResponse.from(historyUseCase
                .getHistory(dossierId, PageQueryParser.parse(page, size, sort, ListSorts.DOSSIER_WORKFLOWS))
                .map(WorkflowLinkResponse::from));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
