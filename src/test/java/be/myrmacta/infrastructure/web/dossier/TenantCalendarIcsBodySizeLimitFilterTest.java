package be.myrmacta.infrastructure.web.dossier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

/** TODO §124 (D-CAL-ICS-SIZE) — refus sur Content-Length avant le multipart Spring. */
class TenantCalendarIcsBodySizeLimitFilterTest {

    private final TenantCalendarIcsBodySizeLimitFilter filter = new TenantCalendarIcsBodySizeLimitFilter(1_024L);

    @Test
    void rejectsAnOversizedPostToCalendarSources() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/tenant_dev/api/tenant/calendar/sources");
        when(request.getContentLengthLong()).thenReturn(2_048L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verifyNoInteractions(chain);
    }

    @Test
    void letsASmallPostThrough_andIgnoresOtherPaths() throws Exception {
        HttpServletRequest ok = mock(HttpServletRequest.class);
        when(ok.getMethod()).thenReturn("POST");
        when(ok.getRequestURI()).thenReturn("/api/tenant/calendar/sources/preview");
        when(ok.getContentLengthLong()).thenReturn(512L);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(ok, response, chain);
        verify(chain).doFilter(ok, response);

        HttpServletRequest other = mock(HttpServletRequest.class);
        when(other.getMethod()).thenReturn("POST");
        when(other.getRequestURI()).thenReturn("/api/tenant/calendar");
        FilterChain otherChain = mock(FilterChain.class);
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        filter.doFilter(other, otherResponse, otherChain);
        verify(otherChain).doFilter(other, otherResponse);
    }
}
