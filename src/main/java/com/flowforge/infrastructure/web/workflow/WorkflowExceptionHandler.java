package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.workflow.model.DecisionEvaluationException;
import com.flowforge.domain.workflow.model.DecisionVersionAlreadyCurrentException;
import com.flowforge.domain.workflow.model.InvalidBpmnException;
import com.flowforge.domain.workflow.model.InvalidDmnException;
import com.flowforge.domain.workflow.model.UnknownDecisionKeyException;
import com.flowforge.domain.workflow.model.UnknownDecisionVersionException;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.model.UnknownWorkflowKeyException;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceMissingException;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.workflow")
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
            UnknownDecisionVersionException.class
    })
    ProblemDetail handleWorkflowResourceNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Workflow resource not found", ex.getMessage());
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

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            InvalidBpmnException.class,
            InvalidDmnException.class,
            DecisionEvaluationException.class
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
