package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.workflow.model.ActivityHistoryEntry;
import be.myrmacta.domain.workflow.model.WorkflowDiagramView;
import be.myrmacta.domain.workflow.model.WorkflowLinkDossierView;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import be.myrmacta.domain.workflow.port.in.ClaimWorkflowTaskUseCase;
import be.myrmacta.domain.workflow.port.in.GetWorkflowDiagramUseCase;
import be.myrmacta.domain.workflow.port.in.GetWorkflowHistoryUseCase;
import be.myrmacta.domain.workflow.port.in.GetWorkflowLinkDossierUseCase;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTasksUseCase;
import be.myrmacta.domain.workflow.port.in.ListAssignableUsersUseCase;
import be.myrmacta.domain.workflow.port.in.ReassignTaskToGroupUseCase;
import be.myrmacta.domain.workflow.port.in.ReassignWorkflowTaskUseCase;
import be.myrmacta.infrastructure.web.ListSorts;
import be.myrmacta.infrastructure.web.PageQueryParser;
import be.myrmacta.infrastructure.web.PageResponse;
import be.myrmacta.infrastructure.web.dossier.AccessContextFactory;
import java.util.List;
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
@RequestMapping("/api/workflow-links/{linkId}")
class WorkflowLinkVisualizationController {

    private final GetWorkflowDiagramUseCase getDiagramUseCase;
    private final GetWorkflowHistoryUseCase getHistoryUseCase;
    private final GetWorkflowTasksUseCase getTasksUseCase;
    private final GetWorkflowLinkDossierUseCase getDossierUseCase;
    private final ReassignWorkflowTaskUseCase reassignUseCase;
    private final ClaimWorkflowTaskUseCase claimUseCase;
    private final ListAssignableUsersUseCase assignableUsersUseCase;
    private final ReassignTaskToGroupUseCase reassignToGroupUseCase;
    private final AccessContextFactory accessContextFactory;

    WorkflowLinkVisualizationController(
            GetWorkflowDiagramUseCase getDiagramUseCase,
            GetWorkflowHistoryUseCase getHistoryUseCase,
            GetWorkflowTasksUseCase getTasksUseCase,
            GetWorkflowLinkDossierUseCase getDossierUseCase,
            ReassignWorkflowTaskUseCase reassignUseCase,
            ClaimWorkflowTaskUseCase claimUseCase,
            ListAssignableUsersUseCase assignableUsersUseCase,
            ReassignTaskToGroupUseCase reassignToGroupUseCase,
            AccessContextFactory accessContextFactory) {
        this.getDiagramUseCase = getDiagramUseCase;
        this.getHistoryUseCase = getHistoryUseCase;
        this.getTasksUseCase = getTasksUseCase;
        this.getDossierUseCase = getDossierUseCase;
        this.reassignUseCase = reassignUseCase;
        this.claimUseCase = claimUseCase;
        this.assignableUsersUseCase = assignableUsersUseCase;
        this.reassignToGroupUseCase = reassignToGroupUseCase;
        this.accessContextFactory = accessContextFactory;
    }

    @GetMapping("/diagram")
    WorkflowDiagramView diagram(@PathVariable UUID linkId, Authentication authentication) {
        return getDiagramUseCase.getDiagram(linkId, accessContextFactory.user(authentication));
    }

    @GetMapping("/history")
    PageResponse<ActivityHistoryEntry> history(
            @PathVariable UUID linkId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(getHistoryUseCase.getHistory(
                linkId,
                accessContextFactory.user(authentication),
                PageQueryParser.parse(page, size, sort, ListSorts.WORKFLOW_LINK_HISTORY)));
    }

    @GetMapping("/tasks")
    PageResponse<WorkflowTaskView> tasks(
            @PathVariable UUID linkId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort,
            Authentication authentication) {
        return PageResponse.from(getTasksUseCase.getTasks(
                linkId,
                accessContextFactory.user(authentication),
                PageQueryParser.parse(page, size, sort, ListSorts.WORKFLOW_LINK_TASKS)));
    }

    @GetMapping("/dossier")
    WorkflowLinkDossierView dossier(@PathVariable UUID linkId, Authentication authentication) {
        return getDossierUseCase.getDossier(linkId, accessContextFactory.user(authentication));
    }

    /**
     * {@code @Transactional} here (same rationale as {@link WorkflowTaskController#complete}, TODO
     * §66 F4): lets Flowable's {@code TaskService.setAssignee(...)} and the {@code TASK_REASSIGNED}
     * notification write roll back together on failure — before this fix the two ran as
     * independent commits, so a notification failure left the reassignment already effective while
     * the caller still saw a 500.
     */
    @PostMapping("/tasks/{taskId}/reassign")
    @Transactional
    ResponseEntity<Void> reassign(
            @PathVariable UUID linkId,
            @PathVariable String taskId,
            @RequestBody ReassignWorkflowTaskRequest request,
            Authentication authentication) {
        reassignUseCase.reassign(
                linkId,
                taskId,
                request == null ? null : request.assigneeId(),
                accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * « M'assigner » (D-ASSIGN-CLAIM) — {@code @Transactional} comme {@link #reassign} : Flowable
     * participe à la transaction ambiante.
     */
    @PostMapping("/tasks/{taskId}/claim")
    @Transactional
    ResponseEntity<Void> claim(
            @PathVariable UUID linkId, @PathVariable String taskId, Authentication authentication) {
        claimUseCase.claim(linkId, taskId, accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

    /**
     * TODO §113 (D-GROUP-REASSIGN) — réaffecter la tâche à un autre groupe (éditeurs du dossier). {@code @Transactional} :
     * tâche, groupe du lien et note de trace changent ensemble.
     */
    @PostMapping("/tasks/{taskId}/reassign-group")
    @Transactional
    ResponseEntity<Void> reassignToGroup(
            @PathVariable UUID linkId,
            @PathVariable String taskId,
            @RequestBody ReassignTaskToGroupRequest request,
            Authentication authentication) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        reassignToGroupUseCase.reassign(linkId, taskId, request.groupCode(), accessContextFactory.user(authentication));
        return ResponseEntity.noContent().build();
    }

    record ReassignTaskToGroupRequest(String groupCode) {}

    /** Utilisateurs connus pouvant modifier le dossier du lien (D-ASSIGN-DIR) — TA/WA. */
    @GetMapping("/assignable-users")
    List<AssignableUserResponse> assignableUsers(@PathVariable UUID linkId, Authentication authentication) {
        return assignableUsersUseCase.listAssignableUsers(linkId, accessContextFactory.user(authentication)).stream()
                .map(AssignableUserResponse::from)
                .toList();
    }
}
