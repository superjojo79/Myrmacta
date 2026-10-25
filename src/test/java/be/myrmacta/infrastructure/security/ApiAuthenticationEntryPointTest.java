package be.myrmacta.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.web.AuthenticationEntryPoint;

class ApiAuthenticationEntryPointTest {

    @Test
    void apiPath_returns401EmptyBody() throws Exception {
        AuthenticationEntryPoint delegate = mock(AuthenticationEntryPoint.class);
        var entryPoint = new ApiAuthenticationEntryPoint(delegate);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setServletPath("/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request, response, new AuthenticationCredentialsNotFoundException("unauthenticated"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void nonApiPath_delegatesToLoginEntryPoint() throws Exception {
        AuthenticationEntryPoint delegate = mock(AuthenticationEntryPoint.class);
        var entryPoint = new ApiAuthenticationEntryPoint(delegate);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/");
        HttpServletResponse response = new MockHttpServletResponse();
        var exception = new AuthenticationCredentialsNotFoundException("unauthenticated");

        entryPoint.commence(request, response, exception);

        verify(delegate).commence(request, response, exception);
    }

    @Test
    void emptyServletPath_usesRequestUriForApiDetection() throws Exception {
        AuthenticationEntryPoint delegate = mock(AuthenticationEntryPoint.class);
        var entryPoint = new ApiAuthenticationEntryPoint(delegate);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("");
        request.setRequestURI("/api/auth/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                request, response, new AuthenticationCredentialsNotFoundException("unauthenticated"));

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
