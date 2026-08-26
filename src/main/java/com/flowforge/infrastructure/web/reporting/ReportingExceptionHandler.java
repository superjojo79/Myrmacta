package com.flowforge.infrastructure.web.reporting;

import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.reporting.model.SlaCohortTooLargeException;
import com.flowforge.domain.reporting.model.TenantSlaSettingsNotFoundException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.reporting")
public class ReportingExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(TenantSlaSettingsNotFoundException.class)
    ProblemDetail handleSettingsNotFound(TenantSlaSettingsNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "SLA settings not found", ex.getMessage());
    }

    @ExceptionHandler(SlaCohortTooLargeException.class)
    ProblemDetail handleCohortTooLarge(SlaCohortTooLargeException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
        problem.setProperty("code", SlaCohortTooLargeException.CODE);
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
