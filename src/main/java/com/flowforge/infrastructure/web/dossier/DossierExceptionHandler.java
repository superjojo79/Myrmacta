package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.IllegalDossierStatusTransitionException;
import com.flowforge.domain.dossier.model.InactiveDossierTypeException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.dossier")
public class DossierExceptionHandler {

    @ExceptionHandler(DossierFieldValidationException.class)
    ProblemDetail handleFieldValidation(DossierFieldValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dossier field validation failed", ex.getMessage());
        problem.setProperty("errors", ex.errors());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Dossier resource not found", ex.getMessage());
    }

    @ExceptionHandler({
            IllegalDossierStatusTransitionException.class,
            DossierConflictException.class,
            InactiveDossierTypeException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    ProblemDetail handleConflict(Exception ex) {
        return problem(HttpStatus.CONFLICT, "Dossier conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Dossier conflict", "Unique constraint or data integrity violation");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
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
