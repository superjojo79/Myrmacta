package be.myrmacta.infrastructure.web.notification.template;

import be.myrmacta.domain.document.model.TemplateProcessingException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateNotFoundException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateStateException;
import be.myrmacta.domain.notification.template.model.NotificationTemplateValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduit les erreurs de l'API des gabarits de notification. Les détails nomment la partie et la règle ou l'expression,
 * jamais une valeur du modèle.
 */
@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.notification.template")
public class NotificationTemplateExceptionHandler {

    @ExceptionHandler({
        NotificationTemplateValidationException.class,
        TemplateProcessingException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        String detail = ex instanceof HttpMessageNotReadableException ? "Malformed request body" : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    @ExceptionHandler(NotificationTemplateNotFoundException.class)
    ProblemDetail handleNotFound(NotificationTemplateNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Notification template not found", ex.getMessage());
    }

    @ExceptionHandler(NotificationTemplateStateException.class)
    ProblemDetail handleInvalidState(NotificationTemplateStateException ex) {
        return problem(HttpStatus.CONFLICT, "Notification template conflict", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String detail = "A conflicting notification template already exists";
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        if (message.contains("uq_notification_template_active")) {
            detail = "An ACTIVE notification template already exists for this key/variant/locale";
        } else if (message.contains("uq_notification_template_version")) {
            detail = "A notification template version already exists for this key/variant/locale";
        }
        return problem(HttpStatus.CONFLICT, "Notification template conflict", detail);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
