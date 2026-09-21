package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

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
}
