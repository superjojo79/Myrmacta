package com.flowforge.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.FlowForgeApplication;
import com.flowforge.infrastructure.tenant.InvalidTenantIdException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
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

    @Test
    void invalidTenantId_failsBootViaApplicationRunner() {
        // Args CLI > application.yml (Builder.properties() serait écrasé par le yaml).
        assertThatThrownBy(() -> new SpringApplicationBuilder(FlowForgeApplication.class)
                        .web(WebApplicationType.NONE)
                        .run("--app.tenant.id=tenant-a"))
                .isInstanceOf(InvalidTenantIdException.class)
                .hasMessageContaining("tenant-a");
    }
}
