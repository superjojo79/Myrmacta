package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Attribut {@code Secure} des cookies session / CSRF (false en HTTP local).
 */
@ConfigurationProperties(prefix = "app.security.cookie")
public record SecurityCookieProperties(@DefaultValue("true") boolean secure) {}
