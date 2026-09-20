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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;

class SecurityHeadersCustomizerTest {

    static final String EXPECTED_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline';"
                    + " img-src 'self' data: blob:; font-src 'self' data:; connect-src 'self';"
                    + " frame-src 'self' blob:; worker-src 'self' blob:;"
                    + " frame-ancestors 'none'; object-src 'none'; base-uri 'self'";

    @Test
    void contentSecurityPolicy_matchesDesignString() {
        assertThat(SecurityHeadersCustomizer.CONTENT_SECURITY_POLICY).isEqualTo(EXPECTED_CSP);
    }

    @Test
    void hstsEnabled_isFalseOnLocalProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        var customizer = new SecurityHeadersCustomizer(new SecurityHeadersProperties(true), env);

        assertThat(customizer.hstsEnabled()).isFalse();
    }

    @Test
    void hstsEnabled_isFalseOnTestProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");
        var customizer = new SecurityHeadersCustomizer(new SecurityHeadersProperties(true), env);

        assertThat(customizer.hstsEnabled()).isFalse();
    }

    @Test
    void hstsEnabled_isTrueOutsideLocalAndTest() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        var customizer = new SecurityHeadersCustomizer(new SecurityHeadersProperties(true), env);

        assertThat(customizer.hstsEnabled()).isTrue();
        assertThat(SecurityHeadersCustomizer.HSTS_MAX_AGE_SECONDS).isEqualTo(31_536_000L);
        assertThat(SecurityHeadersCustomizer.HSTS_INCLUDE_SUBDOMAINS).isTrue();
        assertThat(SecurityHeadersCustomizer.HSTS_PRELOAD).isFalse();
    }

    @Test
    void apply_whenDisabled_doesNotTouchHeaders() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        var customizer = new SecurityHeadersCustomizer(
                new SecurityHeadersProperties(false), new MockEnvironment());

        customizer.apply(http);

        verify(http, never()).headers(any());
    }

    @Test
    void apply_whenEnabled_configuresHeaders() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        when(http.headers(any())).thenReturn(http);
        var customizer = new SecurityHeadersCustomizer(
                new SecurityHeadersProperties(true), new MockEnvironment());

        customizer.apply(http);

        verify(http).headers(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void apply_whenEnabledOutsideLocalTest_configuresHstsMaxAge() throws Exception {
        HttpSecurity http = mock(HttpSecurity.class);
        ArgumentCaptor<Customizer<HeadersConfigurer<HttpSecurity>>> headersCaptor =
                ArgumentCaptor.forClass(Customizer.class);
        when(http.headers(headersCaptor.capture())).thenReturn(http);

        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        new SecurityHeadersCustomizer(new SecurityHeadersProperties(true), env).apply(http);

        HeadersConfigurer<HttpSecurity> headers = mock(HeadersConfigurer.class);
        when(headers.contentSecurityPolicy(any())).thenReturn(headers);
        when(headers.frameOptions(any())).thenReturn(headers);
        when(headers.contentTypeOptions(any())).thenReturn(headers);
        when(headers.referrerPolicy(any())).thenReturn(headers);
        when(headers.httpStrictTransportSecurity(any())).thenReturn(headers);

        headersCaptor.getValue().customize(headers);

        ArgumentCaptor<Customizer<HeadersConfigurer<HttpSecurity>.HstsConfig>> hstsCaptor =
                ArgumentCaptor.forClass(Customizer.class);
        verify(headers).httpStrictTransportSecurity(hstsCaptor.capture());

        HeadersConfigurer<HttpSecurity>.HstsConfig hstsConfig = mock(HeadersConfigurer.HstsConfig.class);
        when(hstsConfig.maxAgeInSeconds(anyLong())).thenReturn(hstsConfig);
        when(hstsConfig.includeSubDomains(anyBoolean())).thenReturn(hstsConfig);
        when(hstsConfig.preload(anyBoolean())).thenReturn(hstsConfig);
        hstsCaptor.getValue().customize(hstsConfig);

        verify(hstsConfig).maxAgeInSeconds(31_536_000L);
        verify(hstsConfig).includeSubDomains(true);
        verify(hstsConfig).preload(false);
    }
}
