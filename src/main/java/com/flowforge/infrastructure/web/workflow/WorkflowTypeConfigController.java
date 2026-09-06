package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow-type-configs")
class WorkflowTypeConfigController {

    private final TenantProperties tenantProperties;
    private final SaveWorkflowTypeConfigUseCase saveUseCase;
    private final GetWorkflowTypeConfigUseCase getUseCase;
    private final AccessContextFactory accessContextFactory;

    WorkflowTypeConfigController(
            TenantProperties tenantProperties,
            SaveWorkflowTypeConfigUseCase saveUseCase,
            GetWorkflowTypeConfigUseCase getUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.saveUseCase = saveUseCase;
        this.getUseCase = getUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    ResponseEntity<WorkflowTypeConfigResponse> create(
            @RequestBody WorkflowTypeConfigRequest request,
            Authentication authentication) {
        AccessContext.User actorContext = accessContextFactory.user(authentication);
        String actor = actorContext.userId();
        WorkflowTypeConfig saved = saveUseCase.create(
                request.toDomain(tenantProperties.id(), UUID.randomUUID(), null, actor, actor), actorContext);
        return ResponseEntity
                .created(URI.create("/api/workflow-type-configs/" + saved.workflowKey()))
                .body(WorkflowTypeConfigResponse.from(saved));
    }

    @PatchMapping("/{workflowKey}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    WorkflowTypeConfigResponse update(
            @PathVariable String workflowKey,
            @RequestBody WorkflowTypeConfigRequest request,
            Authentication authentication) {
        AccessContext.User actorContext = accessContextFactory.user(authentication);
        String actor = actorContext.userId();
        WorkflowTypeConfig existing = getUseCase.getByKey(workflowKey)
                .orElseThrow(() -> new WorkflowTypeConfigNotFoundException("Workflow type config not found: " + workflowKey));
        return WorkflowTypeConfigResponse.from(saveUseCase.update(
                request.toDomain(tenantProperties.id(), existing.id(), workflowKey, existing.createdBy(), actor),
                actorContext));
    }

    @GetMapping
    PageResponse<WorkflowTypeConfigResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        accessContextFactory.requireOidcPrincipal(authentication);
        return PageResponse.from(getUseCase
                .list(PageQueryParser.parse(page, size, sort, ListSorts.WORKFLOW_TYPE_CONFIGS))
                .map(WorkflowTypeConfigResponse::from));
    }

    @GetMapping("/{workflowKey}")
    WorkflowTypeConfigResponse get(@PathVariable String workflowKey, Authentication authentication) {
        accessContextFactory.requireOidcPrincipal(authentication);
        return getUseCase.getByKey(workflowKey)
                .map(WorkflowTypeConfigResponse::from)
                .orElseThrow(() -> new WorkflowTypeConfigNotFoundException("Workflow type config not found: " + workflowKey));
    }
}
