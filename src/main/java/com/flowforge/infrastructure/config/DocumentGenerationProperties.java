package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document.generate")
public record DocumentGenerationProperties(long maxBodyBytes, int rateLimitPerMinute) {

    public DocumentGenerationProperties {
        if (maxBodyBytes <= 0) {
            maxBodyBytes = 262_144L;
        }
        if (rateLimitPerMinute <= 0) {
            rateLimitPerMinute = 30;
        }
    }
}
