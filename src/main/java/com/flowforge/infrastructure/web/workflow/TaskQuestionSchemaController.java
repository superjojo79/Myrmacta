package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.DeleteTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaForTaskUseCase;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import com.flowforge.infrastructure.config.TenantProperties;
import com.flowforge.infrastructure.web.ListSorts;
import com.flowforge.infrastructure.web.PageQueryParser;
import com.flowforge.infrastructure.web.PageResponse;
import com.flowforge.infrastructure.web.dossier.AccessContextFactory;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin CRUD for the {@code user-question} task form kind schemas (TODO §55) — reachable only by
 * {@code TENANT_ADMIN}/{@code WORKFLOW_ADMIN}, same RBAC as BPMN design-time endpoints
 * ({@code WorkflowTypeConfigController}, {@code WorkflowBpmnDeploymentController}), since a schema
 * is a workflow-design artifact, not a {@code Dossier}-scoped one. The {@code /runtime} endpoint
 * below is separate and open to any authenticated task holder (D-USERQ-RUNTIME-SCOPE).
 */
@RestController
@RequestMapping("/api/task-question-schemas")
class TaskQuestionSchemaController {

    private final TenantProperties tenantProperties;
    private final SaveTaskQuestionSchemaUseCase saveUseCase;
    private final GetTaskQuestionSchemaUseCase getUseCase;
    private final DeleteTaskQuestionSchemaUseCase deleteUseCase;
    private final GetTaskQuestionSchemaForTaskUseCase getForTaskUseCase;
    private final AccessContextFactory accessContextFactory;

    TaskQuestionSchemaController(
            TenantProperties tenantProperties,
            SaveTaskQuestionSchemaUseCase saveUseCase,
            GetTaskQuestionSchemaUseCase getUseCase,
            DeleteTaskQuestionSchemaUseCase deleteUseCase,
            GetTaskQuestionSchemaForTaskUseCase getForTaskUseCase,
            AccessContextFactory accessContextFactory) {
        this.tenantProperties = tenantProperties;
        this.saveUseCase = saveUseCase;
        this.getUseCase = getUseCase;
        this.deleteUseCase = deleteUseCase;
        this.getForTaskUseCase = getForTaskUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    ResponseEntity<TaskQuestionSchemaResponse> create(
            @RequestBody CreateTaskQuestionSchemaRequest request, Authentication authentication) {
        AccessContext.User actorContext = accessContextFactory.user(authentication);
        String actor = actorContext.userId();
        TaskQuestionSchema schema = saveUseCase.save(new TaskQuestionSchema(
                UUID.randomUUID(),
                tenantProperties.id(),
                required(request.schemaKey(), "schemaKey"),
                required(request.label(), "label"),
                listOrEmpty(request.fieldDefinitions()),
                actor,
                actor),
                actorContext);
        return ResponseEntity
                .created(URI.create("/api/task-question-schemas/" + schema.schemaKey()))
                .body(TaskQuestionSchemaResponse.from(schema));
    }

    @PutMapping("/{schemaKey}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    TaskQuestionSchemaResponse update(
            @PathVariable String schemaKey,
            @RequestBody UpdateTaskQuestionSchemaRequest request,
            Authentication authentication) {
        AccessContext.User actorContext = accessContextFactory.user(authentication);
        String actor = actorContext.userId();
        TaskQuestionSchema existing = getUseCase.getByKey(schemaKey, actorContext)
                .orElseThrow(() -> new UnknownTaskQuestionSchemaException("Unknown schema: " + schemaKey));
        TaskQuestionSchema updated = existing.withUpdate(
                required(request.label(), "label"), listOrEmpty(request.fieldDefinitions()), actor);
        return TaskQuestionSchemaResponse.from(saveUseCase.save(updated, actorContext));
    }

    @DeleteMapping("/{schemaKey}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    ResponseEntity<Void> delete(@PathVariable String schemaKey, Authentication authentication) {
        deleteUseCase.delete(schemaKey, accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    PageResponse<TaskQuestionSchemaResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(getUseCase
                .list(
                        PageQueryParser.parse(page, size, sort, ListSorts.TASK_QUESTION_SCHEMAS),
                        accessContextFactory.user(authentication))
                .map(TaskQuestionSchemaResponse::from));
    }

    @GetMapping("/{schemaKey}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN','WORKFLOW_ADMIN')")
    TaskQuestionSchemaResponse getByKey(@PathVariable String schemaKey, Authentication authentication) {
        return getUseCase.getByKey(schemaKey, accessContextFactory.user(authentication))
                .map(TaskQuestionSchemaResponse::from)
                .orElseThrow(() -> new UnknownTaskQuestionSchemaException("Unknown schema: " + schemaKey));
    }

    /**
     * Open to the task assignee (or TA/WA), not admin-only — the schema's runtime consumer is
     * whoever holds the {@code userTask} (TODO §57 M8), mirroring
     * {@code GET /api/dossier-types/{code}/document-templates} (TODO §52). {@code taskId} is
     * required so the use case can verify the caller's own task actually references
     * {@code schemaKey} (D-USERQ-RUNTIME-SCOPE) — there is no {@code DossierType}/{@code VIEW}
     * permission to fall back on here.
     */
    @GetMapping("/{schemaKey}/runtime")
    TaskQuestionSchemaRuntimeResponse getForTask(
            @PathVariable String schemaKey, @RequestParam String taskId, Authentication authentication) {
        return TaskQuestionSchemaRuntimeResponse.from(getForTaskUseCase.getForTask(
                taskId, schemaKey, accessContextFactory.user(authentication)));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static List<FieldDefinition> listOrEmpty(List<FieldDefinition> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
