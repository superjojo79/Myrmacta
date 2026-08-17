package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.document.preview")
public record DocumentPreviewProperties(int rateLimitPerMinute) {

    public DocumentPreviewProperties {
        if (rateLimitPerMinute <= 0) {
            rateLimitPerMinute = 120;
        }
    }
}
