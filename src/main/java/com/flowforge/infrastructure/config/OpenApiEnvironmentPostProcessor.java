package com.flowforge.infrastructure.config;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Copies {@code app.openapi.enabled} onto springdoc flags after {@code application.yml} is loaded.
 * springdoc defaults to on ({@code matchIfMissing=true}); YAML placeholders are not applied early
 * enough to turn it off.
 */
public class OpenApiEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean enabled = environment.getProperty("app.openapi.enabled", Boolean.class, false);
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "flowforgeOpenApiGate",
                        Map.of(
                                "springdoc.api-docs.enabled", Boolean.toString(enabled),
                                "springdoc.swagger-ui.enabled", Boolean.toString(enabled))));
    }
}
