package be.myrmacta.infrastructure.web.dossier;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.in.ManageReplyWorkflowUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow « réponse » d'un type de dossier : ce qui démarre quand une réponse à un
 * e-mail rejoint un dossier de ce type. Réservé à l'administrateur du tenant, comme le reste du type.
 */
@RestController
@RequestMapping("/api/dossier-types/{code}/reply-workflow")
@PreAuthorize("hasRole('TENANT_ADMIN')")
public class DossierTypeReplyWorkflowController {

    private final ManageReplyWorkflowUseCase replyWorkflows;

    public DossierTypeReplyWorkflowController(ManageReplyWorkflowUseCase replyWorkflows) {
        this.replyWorkflows = replyWorkflows;
    }

    /** 204 si le type n'en déclare pas. */
    @GetMapping
    public ResponseEntity<ReplyWorkflowResponse> getReplyWorkflow(@PathVariable String code) {
        return replyWorkflows.get(code).map(ReplyWorkflowResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    public ReplyWorkflowResponse setReplyWorkflow(@PathVariable String code, @RequestBody ReplyWorkflowRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        return ReplyWorkflowResponse.from(replyWorkflows.set(code, request.mode(), request.workflowKey(), request.childTypeCode()));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearReplyWorkflow(@PathVariable String code) {
        replyWorkflows.clear(code);
        return ResponseEntity.noContent().build();
    }

    record ReplyWorkflowRequest(ReplyWorkflowSetting.Mode mode, String workflowKey, String childTypeCode) {}

    record ReplyWorkflowResponse(String dossierTypeCode, ReplyWorkflowSetting.Mode mode, String workflowKey, String childTypeCode) {

        static ReplyWorkflowResponse from(ReplyWorkflowSetting setting) {
            return new ReplyWorkflowResponse(setting.dossierTypeCode(), setting.mode(), setting.workflowKey(), setting.childTypeCode());
        }
    }
}
