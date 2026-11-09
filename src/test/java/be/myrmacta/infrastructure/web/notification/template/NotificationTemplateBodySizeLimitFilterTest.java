package be.myrmacta.infrastructure.web.notification.template;

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

class NotificationTemplateBodySizeLimitFilterTest {

    private final NotificationTemplateBodySizeLimitFilter filter =
            new NotificationTemplateBodySizeLimitFilter(1_024L);

    @Test
    void aDeclaredContentLengthAboveTheCeiling_isRejectedBeforeTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/notification-templates");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void aPutOnATemplate_isBoundedTheSameWay() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/notification-templates/abc");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void aContextPath_doesNotHideTheTemplateApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/tenant_dev/api/notification-templates");
        request.setContextPath("/tenant_dev");
        request.setServletPath("/api/notification-templates");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void aMatrixParameter_doesNotHideTheTemplateApi() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("PUT", "/tenant_dev/api/notification-templates;x=1");
        request.setContextPath("/tenant_dev");
        request.setServletPath("/api/notification-templates");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void aGet_isNotBounded() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notification-templates");
        request.setContent(new byte[2_000]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void aChunkedBody_isBoundedByTheBytesActuallyRead() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/notification-templates/preview");
        when(request.getContentLengthLong()).thenReturn(-1L);
        when(request.getInputStream())
                .thenReturn(new DelegatingServletInputStream(new ByteArrayInputStream(new byte[4_000])));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        ArgumentCaptor<HttpServletRequest> captor = ArgumentCaptor.forClass(HttpServletRequest.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(captor.capture(), eq(response));
        HttpServletRequest wrapped = captor.getValue();
        assertThatThrownBy(() -> {
            ServletInputStream in = wrapped.getInputStream();
            byte[] buffer = new byte[256];
            while (in.read(buffer) != -1) {
                // lecture jusqu'au plafond
            }
        }).isInstanceOf(NotificationTemplatePayloadTooLargeException.class);
    }
}
