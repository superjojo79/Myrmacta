package be.myrmacta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.infrastructure.tenant.InvalidTenantIdException;
import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
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
    void invalidTenantId_failsBootViaEnvironmentPostProcessor() {
        // TenantIdEnvironmentValidator (META-INF/spring.factories) ne s'exécute que sur un vrai
        // bootstrap SpringApplication — ApplicationContextRunner ne le déclenche pas, d'où l'usage
        // de SpringApplicationBuilder ici plutôt que withUserConfiguration(TenantConfig.class).
        // Args CLI > application.yml (Builder.properties() serait écrasé par le yaml).
        assertThatThrownBy(() -> new SpringApplicationBuilder(
                                MyrmactaApplication.class, OidcClientTestConfiguration.class)
                        .web(WebApplicationType.NONE)
                        .run("--app.tenant.id=tenant-a"))
                .isInstanceOf(InvalidTenantIdException.class)
                .hasMessageContaining("tenant-a");
    }
}
