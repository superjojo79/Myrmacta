package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.CallbackAmbiguousCorrelationException;
import be.myrmacta.domain.connector.model.CallbackNoMatchingInstanceException;
import be.myrmacta.domain.connector.model.CallbackRetryLaterException;
import be.myrmacta.domain.connector.model.ConnectorInstanceNotFoundException;
import be.myrmacta.domain.connector.model.ConnectorInstanceQuotaExceededException;
import be.myrmacta.domain.connector.model.ConnectorValidationException;
import be.myrmacta.domain.connector.model.UnknownConnectorException;
import be.myrmacta.domain.connector.model.WebhookAuthenticationException;
import be.myrmacta.domain.connector.model.WebhookIdempotencyInFlightException;
import be.myrmacta.domain.dossier.model.DossierFieldValidationException;
import be.myrmacta.infrastructure.security.SecurityAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "be.myrmacta.infrastructure.web.connector")
public class ConnectorExceptionHandler {

    @ExceptionHandler(WebhookAuthenticationException.class)
    ProblemDetail handleWebhookAuthentication(WebhookAuthenticationException ex, HttpServletRequest request) {
        SecurityAuditLog.webhookAuthenticationFailed(request, ex.instanceId());
        return problem(HttpStatus.UNAUTHORIZED, "Webhook authentication failed", ex.getMessage());
    }

    @ExceptionHandler(WebhookEndpointNotFoundException.class)
    ProblemDetail handleWebhookNotFound(WebhookEndpointNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Webhook endpoint not found", ex.getMessage());
    }

    @ExceptionHandler(ConnectorInstanceNotFoundException.class)
    ProblemDetail handleConnectorNotFound(ConnectorInstanceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Connector instance not found", ex.getMessage());
    }

    @ExceptionHandler(ConnectorInstanceQuotaExceededException.class)
    ProblemDetail handleQuotaExceeded(ConnectorInstanceQuotaExceededException ex) {
        return problem(HttpStatus.CONFLICT, "Connector instance quota exceeded", ex.getMessage());
    }

    @ExceptionHandler(WebhookRateLimitExceededException.class)
    ProblemDetail handleRateLimit(WebhookRateLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Webhook rate limit exceeded", ex.getMessage());
    }

    @ExceptionHandler(WebhookPayloadTooLargeException.class)
    ProblemDetail handlePayloadTooLarge(WebhookPayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload too large", ex.getMessage());
    }

    @ExceptionHandler(WebhookIdempotencyInFlightException.class)
    ProblemDetail handleIdempotencyInFlight(WebhookIdempotencyInFlightException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "Webhook delivery in progress",
                ex.getMessage());
    }

    @ExceptionHandler(CallbackNoMatchingInstanceException.class)
    ProblemDetail handleCallbackNoMatchingInstance(CallbackNoMatchingInstanceException ex) {
        return problem(HttpStatus.NOT_FOUND, "No process waiting for this callback", ex.getMessage());
    }

    @ExceptionHandler(CallbackRetryLaterException.class)
    ProblemDetail handleCallbackRetryLater(CallbackRetryLaterException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Callback cannot be applied yet, retry", ex.getMessage());
    }

    @ExceptionHandler(CallbackAmbiguousCorrelationException.class)
    ProblemDetail handleCallbackAmbiguousCorrelation(CallbackAmbiguousCorrelationException ex, HttpServletRequest request) {
        SecurityAuditLog.callbackAmbiguousCorrelation(
                request, ex.messageName(), ex.correlationKey(), ex.matchCount());
        return problem(HttpStatus.CONFLICT, "Ambiguous callback correlation", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        String detail = "A conflicting connector resource already exists";
        Throwable cause = ex.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null ? "" : cause.getMessage().toLowerCase();
        if (message.contains("active_name")
                || message.contains("uq_connector_instance_tenant_name")
                || message.contains("connector_instance")) {
            detail = "A connector with this name already exists";
        }
        return problem(HttpStatus.CONFLICT, "Connector conflict", detail);
    }

    @ExceptionHandler({
            ConnectorValidationException.class,
            UnknownConnectorException.class,
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    /**
     * TODO §102 (revue adversariale) — webhook entrant dont le dossier est refusé à la validation de ses champs. Champs et
     * codes seulement : ni message ni référence d'un autre dossier (doublon) ne partent vers un système externe.
     */
    @ExceptionHandler(DossierFieldValidationException.class)
    ProblemDetail handleDossierFieldValidation(DossierFieldValidationException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Dossier field validation failed",
                "The dossier carried by this event was refused by its type schema");
        problem.setProperty("errors", ex.errors().stream()
                .map(error -> Map.of("field", error.field(), "code", error.code()))
                .toList());
        return problem;
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ProblemDetail handleUnsupported(UnsupportedOperationException ex) {
        return problem(HttpStatus.NOT_IMPLEMENTED, "Connector execution not supported", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
