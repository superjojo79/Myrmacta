package be.myrmacta.infrastructure.web.document;

import be.myrmacta.domain.document.model.InvalidTemplateStateException;
import be.myrmacta.domain.document.model.InvalidTemplateSyntaxException;
import be.myrmacta.domain.document.model.TemplateNotFoundException;
import be.myrmacta.domain.document.model.TemplateValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.document")
public class DocumentTemplateExceptionHandler {

    @ExceptionHandler({
        TemplateValidationException.class,
        InvalidTemplateSyntaxException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    ProblemDetail handleNotFound(TemplateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Document template not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidTemplateStateException.class)
    ProblemDetail handleInvalidState(InvalidTemplateStateException ex) {
        return problem(HttpStatus.CONFLICT, "Document template conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String detail = "A conflicting document template already exists";
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        if (message.contains("uq_template_active") || message.contains("active_template_key")) {
            detail = "An ACTIVE template already exists for this key/variant/locale";
        } else if (message.contains("uq_template_version") || message.contains("ver_variant_key")) {
            detail = "A template version already exists for this key/variant/locale";
        }
        return problem(HttpStatus.CONFLICT, "Document template conflict", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
