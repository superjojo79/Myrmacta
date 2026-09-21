package com.flowforge.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class SecurityHeadersEnvironmentPostProcessorTest {

    private final SecurityHeadersEnvironmentPostProcessor processor = new SecurityHeadersEnvironmentPostProcessor();
    private final SpringApplication application = new SpringApplication();

    @Test
    void localProfile_allowsHeadersDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("app.security.headers.enabled", "false");

        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void testProfile_allowsHeadersDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("app.security.headers.enabled", "false");

        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfile_rejectsHeadersDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.setProperty("app.security.headers.enabled", "false");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SECURITY_HEADERS_ENABLED");
    }

    @Test
    void noProfile_rejectsHeadersDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.security.headers.enabled", "false");

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodProfile_allowsHeadersEnabledOrUnset() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();

        environment.setProperty("app.security.headers.enabled", "true");
        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }
}
