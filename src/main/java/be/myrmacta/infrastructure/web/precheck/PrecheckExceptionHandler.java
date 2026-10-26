package be.myrmacta.infrastructure.web.precheck;

import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierNotFoundException;
import be.myrmacta.domain.precheck.model.PrecheckConflictException;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckDisabledException;
import be.myrmacta.domain.precheck.model.PrecheckNotFoundException;
import be.myrmacta.domain.precheck.model.PrecheckRateLimitedException;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** TODO §106 — erreurs de l'API de pré-vérification ; les messages ne citent aucune valeur de dossier. */
@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.precheck")
class PrecheckExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler({DossierNotFoundException.class, PrecheckNotFoundException.class, PrecheckDisabledException.class})
    ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Pre-check not found", ex.getMessage());
    }

    @ExceptionHandler(PrecheckConflictException.class)
    ProblemDetail handleConflict(PrecheckConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Pre-check conflict", ex.getMessage());
    }

    @ExceptionHandler(PrecheckRateLimitedException.class)
    ProblemDetail handleRateLimited(PrecheckRateLimitedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Pre-check rerun refused", ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", "decision must be ACCEPTED, DISMISSED or null");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
