package be.myrmacta.infrastructure.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;

class CsrfCookieEagerLoadFilterTest {

    private final CsrfCookieEagerLoadFilter filter = new CsrfCookieEagerLoadFilter();

    @Test
    void continuesWhenCsrfTokenAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void resolvesTokenWhenPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CsrfToken token = mock(CsrfToken.class);
        request.setAttribute(CsrfToken.class.getName(), token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(token).getToken();
        verify(chain).doFilter(request, response);
    }
}
