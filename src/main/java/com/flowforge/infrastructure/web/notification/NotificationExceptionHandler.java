package com.flowforge.infrastructure.web.notification;

import com.flowforge.domain.notification.model.NotificationValidationException;
import com.flowforge.domain.notification.model.NotificationWebhookConflictException;
import com.flowforge.domain.notification.model.NotificationWebhookNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.flowforge.infrastructure.web.notification")
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationWebhookNotFoundException.class)
    ProblemDetail handleNotFound(NotificationWebhookNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Notification webhook not found", ex.getMessage());
    }

    @ExceptionHandler({
        NotificationWebhookConflictException.class,
        DataIntegrityViolationException.class
    })
    ProblemDetail handleConflict(Exception ex) {
        String detail = ex instanceof DataIntegrityViolationException
                ? "A conflicting notification webhook already exists"
                : ex.getMessage();
        return problem(HttpStatus.CONFLICT, "Notification webhook conflict", detail);
    }

    @ExceptionHandler({
        NotificationValidationException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
