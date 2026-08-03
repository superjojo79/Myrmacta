package com.flowforge.infrastructure.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;

class MethodSecurityExceptionHandlerTest {

    private final MethodSecurityExceptionHandler handler = new MethodSecurityExceptionHandler();

    @Test
    void mapsSpringAccessDeniedTo403ProblemDetail() {
        ProblemDetail problem = handler.handleSpringAccessDenied(
                new AccessDeniedException("Access Denied"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Access denied");
        assertThat(problem.getDetail()).isEqualTo("Access Denied");
    }

    @Test
    void mapsAuthorizationDeniedWithBlankMessageToDefaultDetail() {
        ProblemDetail problem = handler.handleSpringAccessDenied(
                new AuthorizationDeniedException(""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Access denied");
        assertThat(problem.getDetail()).isEqualTo("Access denied");
    }
}
