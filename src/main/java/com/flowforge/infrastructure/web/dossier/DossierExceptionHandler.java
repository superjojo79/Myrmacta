package com.flowforge.infrastructure.web.dossier;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierConflictException;
import com.flowforge.domain.dossier.model.DossierFieldValidationException;
import com.flowforge.domain.dossier.model.DossierNotEligibleForPurgeException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DuplicateDossierRelationException;
import com.flowforge.domain.dossier.model.IllegalDossierStatusTransitionException;
import com.flowforge.domain.dossier.model.InactiveDossierTypeException;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.domain.dossier.model.InvalidRetentionPolicyException;
import com.flowforge.domain.dossier.model.TenantRetentionSettingsNotFoundException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.dossier")
public class DossierExceptionHandler {

    @ExceptionHandler(DossierFieldValidationException.class)
    ProblemDetail handleFieldValidation(DossierFieldValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dossier field validation failed", ex.getMessage());
        problem.setProperty("errors", ex.errors());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Dossier resource not found", ex.getMessage());
    }

    @ExceptionHandler(TenantRetentionSettingsNotFoundException.class)
    ProblemDetail handleRetentionSettingsNotFound(TenantRetentionSettingsNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Retention settings not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidDossierElementException.class)
    ProblemDetail handleInvalidElement(InvalidDossierElementException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid dossier element", ex.getMessage());
    }

    @ExceptionHandler(InvalidRetentionPolicyException.class)
    ProblemDetail handleInvalidRetentionPolicy(InvalidRetentionPolicyException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid retention policy", ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload too large", "The uploaded file exceeds the maximum allowed size");
    }

    @ExceptionHandler({
            IllegalDossierStatusTransitionException.class,
            DossierConflictException.class,
            DuplicateDossierRelationException.class,
            InactiveDossierTypeException.class,
            DossierNotEligibleForPurgeException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    ProblemDetail handleConflict(Exception ex) {
        return problem(HttpStatus.CONFLICT, "Dossier conflict", ex.getMessage());
    }

    // Unique/FK integrity violations map to 409. Dossier hard-delete (purge) is allowed; remaining
    // integrity errors are still treated as conflicts.
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
