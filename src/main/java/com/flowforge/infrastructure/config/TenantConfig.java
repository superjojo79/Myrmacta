package com.flowforge.infrastructure.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage multi-tenant : properties d'instance + validation regex au boot.
 * Séparé de {@link DomainConfig} (workflow).
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfig {

    @Bean
    ApplicationRunner tenantIdValidationRunner(TenantProperties tenantProperties) {
        return new TenantIdValidationRunner(tenantProperties);
    }
}
