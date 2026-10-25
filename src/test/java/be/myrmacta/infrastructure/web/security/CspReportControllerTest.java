package be.myrmacta.infrastructure.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.infrastructure.config.SecurityHeadersProperties;
import be.myrmacta.infrastructure.security.ClientIpResolver;
import be.myrmacta.infrastructure.security.SecurityHeadersCustomizer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class CspReportControllerTest {

    private static final SecurityHeadersProperties PROPERTIES =
            new SecurityHeadersProperties(true, List.of("127.0.0.0/8"), 60);

    @Test
    void legacyReportUriShape_returnsNoContent() {
        var controller = new CspReportController(allowingRateLimiter(), PROPERTIES, new ClientIpResolver(PROPERTIES));
        String body = "{\"csp-report\":{\"document-uri\":\"https://app.example/x\","
                + "\"violated-directive\":\"script-src\",\"blocked-uri\":\"https://evil.example/x.js\"}}";

        var response = controller.receive(request(), body.getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void modernReportToShape_returnsNoContent() {
        var controller = new CspReportController(allowingRateLimiter(), PROPERTIES, new ClientIpResolver(PROPERTIES));
        String body = "[{\"type\":\"csp-violation\",\"body\":{\"documentURL\":\"https://app.example/x\","
                + "\"effectiveDirective\":\"script-src\",\"blockedURL\":\"https://evil.example/x.js\"}}]";

        var response = controller.receive(request(), body.getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void malformedBody_stillReturnsNoContent() {
        var controller = new CspReportController(allowingRateLimiter(), PROPERTIES, new ClientIpResolver(PROPERTIES));

        var response = controller.receive(request(), "not-json{{{".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void emptyBody_returnsNoContent() {
        var controller = new CspReportController(allowingRateLimiter(), PROPERTIES, new ClientIpResolver(PROPERTIES));

        var response = controller.receive(request(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void rateLimitExceeded_returnsTooManyRequests() {
        CspReportRateLimiter rateLimiter = mock(CspReportRateLimiter.class);
        when(rateLimiter.tryConsume(any(), anyInt())).thenReturn(false);
        var controller = new CspReportController(rateLimiter, PROPERTIES, new ClientIpResolver(PROPERTIES));

        var response = controller.receive(request(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private static CspReportRateLimiter allowingRateLimiter() {
        CspReportRateLimiter rateLimiter = mock(CspReportRateLimiter.class);
        when(rateLimiter.tryConsume(any(), anyInt())).thenReturn(true);
        return rateLimiter;
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", SecurityHeadersCustomizer.CSP_REPORT_PATH);
        request.setRemoteAddr("203.0.113.10");
        return request;
    }
}
