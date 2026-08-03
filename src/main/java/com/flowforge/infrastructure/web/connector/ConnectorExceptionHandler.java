package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.ConnectorInstanceNotFoundException;
import com.flowforge.domain.connector.model.ConnectorValidationException;
import com.flowforge.domain.connector.model.UnknownConnectorException;
import com.flowforge.domain.connector.model.WebhookAuthenticationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.connector")
public class ConnectorExceptionHandler {

    @ExceptionHandler(WebhookAuthenticationException.class)
    ProblemDetail handleWebhookAuthentication(WebhookAuthenticationException ex) {
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

    @ExceptionHandler(WebhookRateLimitExceededException.class)
    ProblemDetail handleRateLimit(WebhookRateLimitExceededException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Webhook rate limit exceeded", ex.getMessage());
    }

    @ExceptionHandler(WebhookPayloadTooLargeException.class)
    ProblemDetail handlePayloadTooLarge(WebhookPayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload too large", ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException ex) {
        return problem(
                HttpStatus.CONFLICT,
                "Connector conflict",
                "A connector with this name already exists");
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
