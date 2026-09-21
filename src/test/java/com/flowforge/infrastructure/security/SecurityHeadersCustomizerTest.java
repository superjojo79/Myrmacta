package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.infrastructure.config.SecurityHeadersProperties;
import java.util.List;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.header.HeaderWriter;

class SecurityHeadersCustomizerTest {

    static final String EXPECTED_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                    + " img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self';"
                    + " frame-src 'self' blob:; worker-src 'self' blob:;"
                    + " frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    private static final List<String> DEFAULT_TRUSTED_PROXY_CIDRS =
            List.of("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

    @Test
    void contentSecurityPolicy_matchesDesignString() {
        assertThat(SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY).isEqualTo(EXPECTED_CSP);
    }

    @Test
    void effectiveContentSecurityPolicy_appendsReportingDirectivesWithContextPath() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("server.servlet.context-path", "/tenant_a");
        var customizer = new SecurityHeadersCustomizer(properties(), env);

        assertThat(customizer.effectiveContentSecurityPolicy())
                .isEqualTo(EXPECTED_CSP
                        + "; report-uri /tenant_a/api/security/csp-reports"
                        + "; report-to csp-endpoint");
    }

    @Test
    void effectiveContentSecurityPolicy_defaultsToEmptyContextPath() {
        var customizer = new SecurityHeadersCustomizer(properties(), new MockEnvironment());

        assertThat(customizer.effectiveContentSecurityPolicy())
                .isEqualTo(EXPECTED_CSP + "; report-uri /api/security/csp-reports; report-to csp-endpoint");
    }

    @Test
    void hstsEnabled_isFalseOnLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        var customizer = new SecurityHeadersCustomizer(properties(), env);

        assertThat(customizer.hstsEnabled()).isFalse();
    }

    @Test
    void hstsEnabled_isFalseOnTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        var customizer = new SecurityHeadersCustomizer(properties(), env);

        assertThat(customizer.hstsEnabled()).isFalse();
    }

    @Test
    void hstsEnabled_isTrueOutsideLocalAndTest() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var customizer = new SecurityHeadersCustomizer(properties(), env);

        assertThat(customizer.hstsEnabled()).isTrue();
        assertThat(SecurityHeadersCustomizer.HSTS_MAX_AGE_SECONDS).isEqualTo(31_536_000L);
        assertThat(SecurityHeadersCustomizer.HSTS_INCLUDE_SUBDOMAINS).isTrue();
        assertThat(SecurityHeadersCustomizer.HSTS_PRELOAD).isFalse();
    }

    @Test
    void apply_whenDisabled_doesNotTouchHeaders() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        var customizer = new SecurityHeadersCustomizer(properties(false), new MockEnvironment());

        customizer.apply(http);

        verify(http, never()).headers(any());
    }

    @Test
    void apply_whenEnabled_configuresHeaders() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        when(http.headers(any())).thenReturn(http);
        var customizer = new SecurityHeadersCustomizer(properties(), new MockEnvironment());

        customizer.apply(http);

        verify(http).headers(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void apply_whenEnabledOutsideLocalTest_configuresHstsMaxAgeAndReportingEndpoints() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        ArgumentCaptor<Customizer<HeadersConfigurer<HttpSecurity>>> headersCaptor =
                ArgumentCaptor.forClass(Customizer.class);
        when(http.headers(headersCaptor.capture())).thenReturn(http);

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        new SecurityHeadersCustomizer(properties(), env).apply(http);

        HeadersConfigurer<HttpSecurity> headers = mock(HeadersConfigurer.class);
        when(headers.contentSecurityPolicy(any())).thenReturn(headers);
        when(headers.frameOptions(any())).thenReturn(headers);
        when(headers.contentTypeOptions(any())).thenReturn(headers);
        when(headers.referrerPolicy(any())).thenReturn(headers);
        when(headers.addHeaderWriter(any())).thenReturn(headers);
        when(headers.httpStrictTransportSecurity(any())).thenReturn(headers);

        headersCaptor.getValue().customize(headers);

        ArgumentCaptor<HeaderWriter> headerWriterCaptor = ArgumentCaptor.forClass(HeaderWriter.class);
        verify(headers).addHeaderWriter(headerWriterCaptor.capture());
        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        headerWriterCaptor.getValue().writeHeaders(request, response);
        assertThat(response.getHeader("Reporting-Endpoints"))
                .isEqualTo("csp-endpoint=\"/api/security/csp-reports\"");

        ArgumentCaptor<Customizer<HeadersConfigurer<HttpSecurity>.HstsConfig>> hstsCaptor =
                ArgumentCaptor.forClass(Customizer.class);
        verify(headers).httpStrictTransportSecurity(hstsCaptor.capture());

        HeadersConfigurer<HttpSecurity>.HstsConfig hstsConfig = mock(HeadersConfigurer.HstsConfig.class);
        when(hstsConfig.maxAgeInSeconds(anyLong())).thenReturn(hstsConfig);
        when(hstsConfig.includeSubDomains(anyBoolean())).thenReturn(hstsConfig);
        when(hstsConfig.preload(anyBoolean())).thenReturn(hstsConfig);
        when(hstsConfig.requestMatcher(any())).thenReturn(hstsConfig);
        hstsCaptor.getValue().customize(hstsConfig);

        verify(hstsConfig).maxAgeInSeconds(31_536_000L);
        verify(hstsConfig).includeSubDomains(true);
        verify(hstsConfig).preload(false);
        verify(hstsConfig).requestMatcher(any());
    }

    @Test
    void shouldWriteHsts_requiresSecureAndTrustedPeer() {
        var customizer = new SecurityHeadersCustomizer(properties(), new MockEnvironment());

        assertThat(customizer.shouldWriteHsts(request("127.0.0.1", true))).isTrue();
        assertThat(customizer.shouldWriteHsts(request("203.0.113.10", true))).isFalse();
        assertThat(customizer.shouldWriteHsts(request("127.0.0.1", false))).isFalse();
        assertThat(customizer.shouldWriteHsts(request("10.1.2.3", true))).isTrue();
        assertThat(customizer.shouldWriteHsts(request("::1", true))).isTrue();

        MockHttpServletRequest blank = request("127.0.0.1", true);
        blank.setRemoteAddr("   ");
        assertThat(customizer.shouldWriteHsts(blank)).isFalse();

        MockHttpServletRequest missing = request("127.0.0.1", true);
        missing.setRemoteAddr(null);
        assertThat(customizer.shouldWriteHsts(missing)).isFalse();

        assertThat(customizer.shouldWriteHsts(request("not-an-ip", true))).isFalse();
    }

    @Test
    void shouldWriteHsts_honorsConfiguredTrustedProxyCidrs() {
        var customizer = new SecurityHeadersCustomizer(
                new SecurityHeadersProperties(true, List.of("203.0.113.0/24"), 60), new MockEnvironment());

        // A peer that would be trusted by the (unconfigured) RFC1918 default is now rejected...
        assertThat(customizer.shouldWriteHsts(request("10.1.2.3", true))).isFalse();
        // ...and a peer outside RFC1918 explicitly allowlisted is now trusted.
        assertThat(customizer.shouldWriteHsts(request("203.0.113.10", true))).isTrue();
    }

    @Test
    void immediateTcpPeer_unwrapsForwardedForClient() {
        MockHttpServletRequest tcpPeer = request("127.0.0.1", true);
        tcpPeer.setRemoteAddr("127.0.0.1");
        HttpServletRequestWrapper forwarded = new HttpServletRequestWrapper(tcpPeer) {
            @Override
            public String getRemoteAddr() {
                return "203.0.113.50";
            }

            @Override
            public boolean isSecure() {
                return true;
            }
        };

        var customizer = new SecurityHeadersCustomizer(properties(), new MockEnvironment());
        assertThat(SecurityHeadersCustomizer.immediateTcpPeer(forwarded)).isEqualTo("127.0.0.1");
        assertThat(customizer.shouldWriteHsts(forwarded)).isTrue();
    }

    @Test
    void immediateTcpPeer_stopsWhenInnerIsNotHttpServletRequest() {
        jakarta.servlet.ServletRequest notHttp = mock(jakarta.servlet.ServletRequest.class);
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request("192.168.1.10", true)) {
            @Override
            public jakarta.servlet.ServletRequest getRequest() {
                return notHttp;
            }
        };
        assertThat(SecurityHeadersCustomizer.immediateTcpPeer(wrapper)).isEqualTo("192.168.1.10");
    }

    private static MockHttpServletRequest request(String remoteAddr, boolean secure) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRemoteAddr(remoteAddr);
        request.setSecure(secure);
        return request;
    }

    private static SecurityHeadersProperties properties() {
        return properties(true);
    }

    private static SecurityHeadersProperties properties(boolean enabled) {
        return new SecurityHeadersProperties(enabled, DEFAULT_TRUSTED_PROXY_CIDRS, 60);
    }
}
