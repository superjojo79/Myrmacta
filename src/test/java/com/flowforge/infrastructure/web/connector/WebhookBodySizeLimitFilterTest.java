package com.flowforge.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
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
        verify(chain).doFilter(request, response);
    }

    @Test
    void allowsRequestWithoutContentLength() throws Exception {
        // Chunked transfer-encoding — no Content-Length header; the app-level byte[] length
        // check in WebhookController is the remaining backstop for this case.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/webhooks/any");
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }
}
