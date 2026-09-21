package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SecurityHeadersPropertiesTest {

    @Test
    void defaultsEnabledToTrueWhenMissing() {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of()));
        SecurityHeadersProperties props = binder.bindOrCreate(
                "app.security.headers", Bindable.of(SecurityHeadersProperties.class));

        assertThat(props.enabled()).isTrue();
    }

    @Test
    void bindsEnabledFalseOverride() {
        var binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.security.headers.enabled", "false")));
        SecurityHeadersProperties props = binder
                .bind("app.security.headers", Bindable.of(SecurityHeadersProperties.class))
                .get();

        assertThat(props.enabled()).isFalse();
    }

    @Test
    void defaultsTrustedProxyCidrsToLoopbackAndRfc1918WhenMissing() {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of()));
        SecurityHeadersProperties props = binder.bindOrCreate(
                "app.security.headers", Bindable.of(SecurityHeadersProperties.class));

        assertThat(props.trustedProxyCidrs())
                .containsExactly("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");
    }

    @Test
    void bindsTrustedProxyCidrsOverride() {
        var binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.security.headers.trusted-proxy-cidrs", "203.0.113.5/32")));
        SecurityHeadersProperties props = binder
                .bind("app.security.headers", Bindable.of(SecurityHeadersProperties.class))
                .get();

        assertThat(props.trustedProxyCidrs()).isEqualTo(List.of("203.0.113.5/32"));
    }

    @Test
    void defaultsCspReportRateLimitPerMinuteTo60WhenMissing() {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of()));
        SecurityHeadersProperties props = binder.bindOrCreate(
                "app.security.headers", Bindable.of(SecurityHeadersProperties.class));

        assertThat(props.cspReportRateLimitPerMinute()).isEqualTo(60);
    }

    @Test
    void bindsCspReportRateLimitPerMinuteOverride() {
        var binder = new Binder(new MapConfigurationPropertySource(
                Map.of("app.security.headers.csp-report-rate-limit-per-minute", "10")));
        SecurityHeadersProperties props = binder
                .bind("app.security.headers", Bindable.of(SecurityHeadersProperties.class))
                .get();

        assertThat(props.cspReportRateLimitPerMinute()).isEqualTo(10);
    }
}
