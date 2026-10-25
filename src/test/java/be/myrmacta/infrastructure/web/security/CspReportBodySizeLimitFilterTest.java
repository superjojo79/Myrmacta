package be.myrmacta.infrastructure.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CspReportBodySizeLimitFilterTest {

    @Test
    void rejectsDeclaredContentLengthAboveLimit() throws Exception {
        var filter = new CspReportBodySizeLimitFilter(10);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/security/csp-reports");
        request.setContent(new byte[20]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test
    void allowsBodyAtOrBelowLimit() throws Exception {
        var filter = new CspReportBodySizeLimitFilter(10);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/security/csp-reports");
        request.setContent(new byte[10]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
