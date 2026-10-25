package be.myrmacta.infrastructure.config;

import org.springframework.context.annotation.Configuration;

/**
 * Shared domain wiring lives in feature-specific configuration classes. Legacy
 * in-memory workflow beans are intentionally no longer exposed in production.
 */
@Configuration
public class DomainConfig {}
