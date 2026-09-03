package com.flowforge.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WebhookBodySizeLimitFilterTest {

    private final WebhookBodySizeLimitFilter filter = new WebhookBodySizeLimitFilter(1_048_576L);

    @Test
    void rejectsRequestDeclaringOversizedContentLength() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/any");
        request.setContentType("application/json");
        request.setContent(new byte[2_000_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsRequestWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/any");
        request.setContentType("application/json");
        request.setContent(new byte[512]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
    }

    @Test
    void allowsRequestWithoutContentLength() throws Exception {
        // Chunked transfer-encoding — no Content-Length header; the wrapped InputStream (see
        // limitsActualBytesReadEvenWithoutDeclaredContentLength below) is what actually bounds
        // this case now, not the WebhookController byte[] length check -- by the time that check
        // runs, Spring has already fully materialized the body in memory.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/any");
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), eq(response));
    }

    @Test
    void limitsActualBytesReadEvenWithoutDeclaredContentLength() throws Exception {
        // D-CALLBACK-DOS (found in adversarial security review) : WebhookBodySizeLimitFilter only
        // checked the declared Content-Length header -- a chunked request (or one lying about its
        // length) sailed through, and Spring's HttpMessageConverter would then read the entire
        // stream into a byte[] in memory *before* WebhookController's own length check ever ran.
        // The fix must bound the number of bytes actually read from the stream, independent of
        // what the request claims.
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getContentLengthLong()).thenReturn(-1L);
        byte[] oversized = new byte[2_000_000];
        when(request.getInputStream()).thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(oversized)));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(captor.capture(), eq(response));
        HttpServletRequest wrapped = captor.getValue();

        assertThatThrownBy(() -> {
            ServletInputStream in = wrapped.getInputStream();
            byte[] buffer = new byte[8192];
            while (in.read(buffer) != -1) {
                // keep reading until the wrapper trips or the stream is exhausted
            }
        }).isInstanceOf(WebhookPayloadTooLargeException.class);
    }
}
