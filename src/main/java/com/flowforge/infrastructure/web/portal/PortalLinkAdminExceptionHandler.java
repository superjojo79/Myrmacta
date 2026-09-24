package com.flowforge.infrastructure.web.portal;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** TODO §68 — erreurs de l'API instructeur des liens portail. */
@RestControllerAdvice(assignableTypes = PortalLinkAdminController.class)
class PortalLinkAdminExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Portal link resource not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid portal link request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Portal link conflict", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
