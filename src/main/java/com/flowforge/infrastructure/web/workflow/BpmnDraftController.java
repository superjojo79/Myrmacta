package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.workflow.model.BpmnDraft;
import com.flowforge.domain.workflow.port.in.GetBpmnDraftUseCase;
import com.flowforge.domain.workflow.port.in.SaveBpmnDraftUseCase;
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
@RequestMapping("/api/workflow-bpmn-drafts")
@PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
class BpmnDraftController {

    private final GetBpmnDraftUseCase getUseCase;
    private final SaveBpmnDraftUseCase saveUseCase;
    private final AccessContextFactory accessContextFactory;

    BpmnDraftController(
            GetBpmnDraftUseCase getUseCase,
            SaveBpmnDraftUseCase saveUseCase,
            AccessContextFactory accessContextFactory) {
        this.getUseCase = getUseCase;
        this.saveUseCase = saveUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/{processDefinitionKey}")
    ResponseEntity<BpmnDraftResponse> get(@PathVariable String processDefinitionKey) {
        return getUseCase
                .get(processDefinitionKey)
                .map(BpmnDraftResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{processDefinitionKey}")
    BpmnDraftResponse save(
            @PathVariable String processDefinitionKey,
            @RequestBody SaveBpmnDraftRequest request,
            Authentication authentication) {
        String updatedBy = accessContextFactory.user(authentication).userId();
        BpmnDraft draft = saveUseCase.save(processDefinitionKey, request.xml(), updatedBy);
        return BpmnDraftResponse.from(draft);
    }
}

record SaveBpmnDraftRequest(String xml) {}

record BpmnDraftResponse(String processDefinitionKey, String xml, Instant updatedAt, String updatedBy) {

    static BpmnDraftResponse from(BpmnDraft draft) {
        return new BpmnDraftResponse(
                draft.processDefinitionKey(), draft.xml(), draft.updatedAt(), draft.updatedBy());
    }
}
