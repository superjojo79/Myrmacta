package be.myrmacta.infrastructure.web.workflow;

import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import be.myrmacta.domain.dossier.model.WorkflowAlreadyRunningException;
import be.myrmacta.domain.workflow.model.WorkflowTaskAlreadyAssignedException;
import be.myrmacta.domain.workflow.model.DecisionEvaluationException;
import be.myrmacta.domain.expression.model.ExpressionEvaluationException;
import be.myrmacta.domain.expression.model.InvalidExpressionException;
import be.myrmacta.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import be.myrmacta.domain.workflow.model.InvalidBpmnException;
import be.myrmacta.domain.workflow.model.InvalidDmnException;
import be.myrmacta.domain.workflow.model.UnknownBlockReferenceException;
import be.myrmacta.domain.workflow.model.UnknownBlockSourceException;
import be.myrmacta.domain.workflow.model.UnknownDecisionKeyException;
import be.myrmacta.domain.workflow.model.UnknownDecisionVersionException;
import be.myrmacta.domain.workflow.model.UnknownTaskQuestionSchemaException;
import be.myrmacta.domain.workflow.model.UnknownWorkflowKeyException;
import be.myrmacta.domain.workflow.model.WorkflowProcessInstanceMissingException;
import be.myrmacta.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import be.myrmacta.domain.workflow.model.WorkflowTaskNotFoundException;
import be.myrmacta.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.workflow")
class WorkflowExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleDossierNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Dossier resource not found", ex.getMessage());
    }

    @ExceptionHandler({
            DossierWorkflowLinkNotFoundException.class,
            WorkflowProcessInstanceNotFoundException.class,
            WorkflowTypeConfigNotFoundException.class,
            WorkflowTaskNotFoundException.class,
            UnknownWorkflowKeyException.class,
            UnknownTaskQuestionSchemaException.class,
            UnknownDecisionKeyException.class,
            UnknownBlockSourceException.class,
            UnknownBlockReferenceException.class,
            be.myrmacta.domain.workflow.model.WorkflowIncidentNotFoundException.class,
            UnknownDecisionVersionException.class
    })
    ProblemDetail handleWorkflowResourceNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Workflow resource not found", ex.getMessage());
    }

    @ExceptionHandler({
            be.myrmacta.domain.workflow.model.DraftConflictException.class,
            be.myrmacta.domain.workflow.model.DraftChangedException.class
    })
    ProblemDetail handleDraftConflict(RuntimeException ex) {
        return problem(HttpStatus.CONFLICT, "Draft conflict", ex.getMessage());
    }

    @ExceptionHandler(be.myrmacta.domain.workflow.model.DraftNotFoundException.class)
    ProblemDetail handleDraftNotFound(be.myrmacta.domain.workflow.model.DraftNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Draft not found", ex.getMessage());
    }

    @ExceptionHandler(be.myrmacta.domain.workflow.model.DirectPublicationNotAllowedException.class)
    ProblemDetail handleDirectPublicationRefused(be.myrmacta.domain.workflow.model.DirectPublicationNotAllowedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Direct publication not allowed", ex.getMessage());
    }

    @ExceptionHandler(DecisionVersionAlreadyCurrentException.class)
    ProblemDetail handleVersionAlreadyCurrent(DecisionVersionAlreadyCurrentException ex) {
        return problem(HttpStatus.CONFLICT, "Decision version already current", ex.getMessage());
    }

    @ExceptionHandler(WorkflowAlreadyRunningException.class)
    ProblemDetail handleAlreadyRunning(WorkflowAlreadyRunningException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", ex.getMessage());
    }

    @ExceptionHandler(WorkflowProcessInstanceMissingException.class)
    ProblemDetail handleProcessInstanceMissing(WorkflowProcessInstanceMissingException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow process instance missing", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", "Unique constraint or data integrity violation");
    }

    /** TODO §94 — le job a encore des tentatives automatiques : seul un incident (dead letter) se rejoue. */
    @ExceptionHandler(be.myrmacta.domain.workflow.model.IncidentNotRetryableException.class)
    ProblemDetail handleIncidentNotRetryable(be.myrmacta.domain.workflow.model.IncidentNotRetryableException ex) {
        return problem(HttpStatus.CONFLICT, "Incident not retryable", ex.getMessage());
    }

    @ExceptionHandler(WorkflowTaskAlreadyAssignedException.class)
    ProblemDetail handleTaskAlreadyAssigned(WorkflowTaskAlreadyAssignedException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow task already assigned", ex.getMessage());
    }

    /**
     * TODO §93 (revue M1) — une brique « Notifier » synchrone qui échoue (destinataire lu dans le dossier vide ou mal formé)
     * fait échouer la complétion ou le démarrage. 422 plutôt que 500 : la requête est valide, c'est une donnée du dossier qui
     * empêche l'étape. Le message nomme la source (dossierField:…), jamais l'adresse.
     */
    @ExceptionHandler(be.myrmacta.domain.notification.model.NotificationValidationException.class)
    ProblemDetail handleWorkflowStepFailed(be.myrmacta.domain.notification.model.NotificationValidationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Workflow step failed", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            InvalidBpmnException.class,
            InvalidDmnException.class,
            DecisionEvaluationException.class,
            InvalidExpressionException.class,
            ExpressionEvaluationException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
