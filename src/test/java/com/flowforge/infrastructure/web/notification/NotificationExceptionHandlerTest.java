package com.flowforge.infrastructure.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.notification.model.NotificationWebhookConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class NotificationExceptionHandlerTest {

    private final NotificationExceptionHandler handler = new NotificationExceptionHandler();

    @Test
    void conflictException_returns409WithMessage() {
        ProblemDetail problem =
                handler.handleConflict(new NotificationWebhookConflictException("name taken"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Notification webhook conflict");
        assertThat(problem.getDetail()).isEqualTo("name taken");
    }

    @Test
    void dataIntegrity_returns409SanitizedDetail() {
        ProblemDetail problem =
                handler.handleConflict(new DataIntegrityViolationException("uk_violation"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).isEqualTo("A conflicting notification webhook already exists");
    }
}
