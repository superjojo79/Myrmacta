package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.in.GetWorkflowTypeConfigUseCase;
import com.flowforge.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    ResponseEntity<WorkflowTypeConfigResponse> create(
            @RequestBody WorkflowTypeConfigRequest request,
            Authentication authentication) {
        requireWorkflowAdmin(authentication);
        WorkflowTypeConfig saved = saveUseCase.create(request.toDomain(tenantProperties.id(), UUID.randomUUID(), null));
        return ResponseEntity
                .created(URI.create("/api/workflow-type-configs/" + saved.workflowKey()))
                .body(WorkflowTypeConfigResponse.from(saved));
    }

    @PatchMapping("/{workflowKey}")
    WorkflowTypeConfigResponse update(
            @PathVariable String workflowKey,
            @RequestBody WorkflowTypeConfigRequest request,
            Authentication authentication) {
        requireWorkflowAdmin(authentication);
        WorkflowTypeConfig existing = getUseCase.getByKey(workflowKey)
                .orElseThrow(() -> new IllegalArgumentException("Workflow type config not found: " + workflowKey));
        return WorkflowTypeConfigResponse.from(saveUseCase.update(
                request.toDomain(tenantProperties.id(), existing.id(), workflowKey)));
    }

    @GetMapping
    List<WorkflowTypeConfigResponse> list(Authentication authentication) {
        accessContextFactory.user(authentication);
        return getUseCase.list().stream().map(WorkflowTypeConfigResponse::from).toList();
    }

    @GetMapping("/{workflowKey}")
    WorkflowTypeConfigResponse get(@PathVariable String workflowKey, Authentication authentication) {
        accessContextFactory.user(authentication);
        return getUseCase.getByKey(workflowKey)
                .map(WorkflowTypeConfigResponse::from)
                .orElseThrow(() -> new IllegalArgumentException("Workflow type config not found: " + workflowKey));
    }

    private void requireWorkflowAdmin(Authentication authentication) {
        AccessContext.User user = accessContextFactory.user(authentication);
        if (!user.hasRole("TENANT_ADMIN") && !user.hasRole("WORKFLOW_ADMIN")) {
            throw new AccessDeniedException("WORKFLOW_ADMIN or TENANT_ADMIN role required");
        }
    }
}
