package com.flowforge.infrastructure.web.workflow;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.WorkflowAlreadyRunningException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTypeConfigNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.workflow")
class WorkflowExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleDossierNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Dossier resource not found", ex.getMessage());
    }

    @ExceptionHandler({
            DossierWorkflowLinkNotFoundException.class,
            WorkflowTypeConfigNotFoundException.class,
            WorkflowTaskNotFoundException.class
    })
    ProblemDetail handleWorkflowResourceNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Workflow resource not found", ex.getMessage());
    }

    @ExceptionHandler(WorkflowAlreadyRunningException.class)
    ProblemDetail handleAlreadyRunning(WorkflowAlreadyRunningException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", "Unique constraint or data integrity violation");
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleIllegalState(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Workflow conflict", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
