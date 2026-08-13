package com.flowforge.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Câblage multi-tenant : properties d'instance. La validation regex de {@code app.tenant.id} se
 * fait avant tout bean, voir {@link com.flowforge.infrastructure.tenant.TenantIdEnvironmentValidator}
 * (enregistré via {@code META-INF/spring.factories}).
 * Séparé de {@link DomainConfig} (workflow).
 */
@Configuration
@EnableConfigurationProperties(TenantProperties.class)
public class TenantConfig {
}
