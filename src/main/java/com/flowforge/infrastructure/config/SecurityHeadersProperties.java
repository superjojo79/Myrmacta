package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Kill switch du paquet d'en-têtes HTTP A4 (CSP / HSTS / XFO / nosniff / Referrer-Policy).
 */
@ConfigurationProperties(prefix = "app.security.headers")
public record SecurityHeadersProperties(@DefaultValue("true") boolean enabled) {}
