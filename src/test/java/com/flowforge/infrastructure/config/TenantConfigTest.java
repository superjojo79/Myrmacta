package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@SpringBootTest
class TenantConfigTest {

    @Autowired
    private TenantProperties tenantProperties;

    @Test
    void bindsTenantDevFromApplicationYml() {
        assertThat(tenantProperties.id()).isEqualTo("tenant_dev");
    }

    @Test
    void missingTenantId_failsContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(TenantConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }

    @Test
    void blankTenantId_failsContext() {
        new ApplicationContextRunner()
                .withUserConfiguration(TenantConfig.class)
                .withPropertyValues("app.tenant.id=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).isNotNull();
                });
    }
}
