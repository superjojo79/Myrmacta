package be.myrmacta.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openapi")
public record OpenApiProperties(boolean enabled) {}
