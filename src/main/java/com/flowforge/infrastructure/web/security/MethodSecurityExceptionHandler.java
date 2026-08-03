package com.flowforge.infrastructure.web.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bridges Spring method-security denials to RFC 7807 {@link ProblemDetail} 403.
 * Domain {@code AccessDeniedException} remains handled by dossier/workflow advices.
 */
@RestControllerAdvice
public class MethodSecurityExceptionHandler {

    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    ProblemDetail handleSpringAccessDenied(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Access denied"
                        : ex.getMessage());
        problem.setTitle("Access denied");
        return problem;
    }
}
