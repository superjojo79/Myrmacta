package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.DmnDraft;
import com.flowforge.domain.workflow.port.in.GetDmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveDmnDraftUseCase;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dmn-decision-drafts")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class DmnDraftController {

    private final GetDmnDraftUseCase getUseCase;
    private final SaveDmnDraftUseCase saveUseCase;
    private final AccessContextFactory accessContextFactory;

    DmnDraftController(
            GetDmnDraftUseCase getUseCase, SaveDmnDraftUseCase saveUseCase, AccessContextFactory accessContextFactory) {
        this.getUseCase = getUseCase;
        this.saveUseCase = saveUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/{decisionKey}")
    ResponseEntity<DmnDraftResponse> get(@PathVariable String decisionKey) {
        return getUseCase
                .get(decisionKey)
                .map(DmnDraftResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{decisionKey}")
    DmnDraftResponse save(
            @PathVariable String decisionKey, @RequestBody SaveDmnDraftRequest request, Authentication authentication) {
        String updatedBy = accessContextFactory.user(authentication).userId();
        DmnDraft draft = saveUseCase.save(decisionKey, request.xml(), updatedBy);
        return DmnDraftResponse.from(draft);
    }
}

record SaveDmnDraftRequest(String xml) {}

record DmnDraftResponse(String decisionKey, String xml, Instant updatedAt, String updatedBy) {

    static DmnDraftResponse from(DmnDraft draft) {
        return new DmnDraftResponse(draft.decisionKey(), draft.xml(), draft.updatedAt(), draft.updatedBy());
    }
}
