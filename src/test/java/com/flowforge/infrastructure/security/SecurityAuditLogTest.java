package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

class SecurityAuditLogTest {

    @Test
    void tenantMismatch_doesNotThrow() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dossiers");

        assertThatCode(() -> SecurityAuditLog.tenantMismatch(request, "other_tenant"))
                .doesNotThrowAnyException();
    }

    @Test
    void accessDenied_withAuthenticatedPrincipal_doesNotThrow() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dossiers/1");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("u1");

        assertThatCode(() -> SecurityAuditLog.accessDenied(request, authentication, "Access denied for permission VIEW"))
                .doesNotThrowAnyException();
    }

    @Test
    void accessDenied_withoutAuthentication_doesNotThrow() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dossiers/1");

        assertThatCode(() -> SecurityAuditLog.accessDenied(request, null, "denied"))
                .doesNotThrowAnyException();
    }
}
