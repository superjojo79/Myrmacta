package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.flowforge.infrastructure.config.TenantProperties;
import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class TenantResolutionFilterTest {

    private final TenantResolutionFilter filter =
            new TenantResolutionFilter(new TenantProperties("tenant_dev"));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void skipsWhenUnauthenticated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void continuesWhenTenantClaimMatches() throws Exception {
        authenticateWithTenant("tenant_dev");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(request.getSession(false)).isNotNull();
    }

    @Test
    void forbidsAndInvalidatesSession_whenTenantMismatches() throws Exception {
        authenticateWithTenant("tenant_a");
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void forbidsAndInvalidatesSession_whenTenantClaimMissing() throws Exception {
        authenticateWithTenant(null);
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void forbidsAndInvalidatesSession_whenTenantClaimBlank() throws Exception {
        authenticateWithTenant("");
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(session.isInvalid()).isTrue();
    }

    private static void authenticateWithTenant(String tenantId) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", "user-1");
        if (tenantId != null) {
            claims.put("tenant_id", tenantId);
        }
        OidcIdToken idToken =
                new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(60), claims);
        var user = new DefaultOidcUser(List.of(new OidcUserAuthority(idToken)), idToken);
        var authentication = new OAuth2AuthenticationToken(user, user.getAuthorities(), "oidc");
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
