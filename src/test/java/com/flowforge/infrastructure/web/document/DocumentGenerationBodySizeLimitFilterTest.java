package com.flowforge.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class DocumentGenerationBodySizeLimitFilterTest {

    private final DocumentGenerationBodySizeLimitFilter filter =
            new DocumentGenerationBodySizeLimitFilter(1_024L);

    @Test
    void rejectsOversizedGenerateRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/generate");
        request.setContentType("application/json");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void allowsGenerateRequestWithinLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/documents/generate");
        request.setContentType("application/json");
        request.setContent(new byte[512]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsOversizedPreviewRequest() throws Exception {
        assertRejected("POST", "/api/documents/preview");
    }

    @Test
    void rejectsOversizedPreviewPdfRequest() throws Exception {
        assertRejected("POST", "/api/documents/preview/pdf");
    }

    @Test
    void ignoresOversizedRequestsOnOtherPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dossiers");
        request.setContentType("application/json");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    @Test
    void ignoresNonPostRequestsOnGeneratePath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/documents/generate");
        request.setContentType("application/json");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }

    private void assertRejected(String method, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setContentType("application/json");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }
}
