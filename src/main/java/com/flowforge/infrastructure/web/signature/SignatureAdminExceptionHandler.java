package com.flowforge.infrastructure.web.signature;

import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** TODO §72 — erreurs de l'API instructeur des demandes de signature. */
@RestControllerAdvice(assignableTypes = {SignatureAdminController.class, MySignaturesController.class})
class SignatureAdminExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    /** Signataire interne sans accès (autre utilisateur, pas son tour, demande close, dossier illisible). */
    @ExceptionHandler(com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException.class)
    ProblemDetail handleNoAccess(com.flowforge.domain.workflow.portal.model.PortalAccessDeniedException ex) {
        return problem(HttpStatus.NOT_FOUND, "Signature resource not found", "Ressource introuvable.");
    }

    @ExceptionHandler(DossierNotFoundException.class)
    ProblemDetail handleNotFound(DossierNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Signature resource not found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleInvalid(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid signature request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, "Signature conflict", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
