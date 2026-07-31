package com.flowforge.infrastructure.config;

import com.flowforge.infrastructure.tenant.TenantIdValidator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Fail-fast au démarrage si {@link TenantProperties#id()} n'est pas conforme à la regex.
 */
public final class TenantIdValidationRunner implements ApplicationRunner {

    private final TenantProperties tenantProperties;

    public TenantIdValidationRunner(TenantProperties tenantProperties) {
        this.tenantProperties = tenantProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantIdValidator.validate(tenantProperties.id());
    }
}
