package com.flowforge.infrastructure.web.document;

import com.flowforge.domain.document.model.DocumentRenderException;
import com.flowforge.domain.document.model.DocumentStorageException;
import com.flowforge.domain.document.model.TemplateNotFoundException;
import com.flowforge.domain.document.model.TemplateProcessingException;
import com.flowforge.domain.document.model.TemplateValidationException;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierElementNotFoundException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.InvalidDossierElementException;
import com.flowforge.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(
        assignableTypes = {DocumentGenerationController.class, DossierElementContentController.class})
public class DocumentGenerationExceptionHandler {

    @ExceptionHandler({
        TemplateValidationException.class,
        TemplateProcessingException.class,
        DocumentRenderException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler({
        TemplateNotFoundException.class,
        DossierElementNotFoundException.class,
        DossierNotFoundException.class,
        DocumentStorageException.class
    })
    ProblemDetail handleNotFound(RuntimeException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidDossierElementException.class)
    ProblemDetail handleConflict(InvalidDossierElementException ex) {
        return problem(HttpStatus.CONFLICT, "Dossier element conflict", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request, Authentication authentication) {
        SecurityAuditLog.accessDenied(request, authentication, ex.getMessage());
        return problem(HttpStatus.FORBIDDEN, "Access denied", ex.getMessage());
    }

    @ExceptionHandler(DocumentGenerationRateLimitExceededException.class)
    ProblemDetail handleRateLimit(DocumentGenerationRateLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
