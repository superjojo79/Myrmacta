package be.myrmacta.infrastructure.embeddeddb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class EmbeddedDbEnvironmentPostProcessorTest {

    private static MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment().withProperty("app.environment", "local");
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private static SpringApplication application() {
        return new SpringApplication(Object.class);
    }

    @Test
    void profileInactive_doesNothing_andDoesNotLookForALauncher() {
        MockEnvironment environment = environment("local");
        new EmbeddedDbEnvironmentPostProcessor(() -> {
            throw new AssertionError("launcher must not be looked up");
        }).postProcessEnvironment(environment, application());

        assertThat(environment.getPropertySources().contains(EmbeddedDbEnvironmentPostProcessor.PROPERTY_SOURCE_NAME))
                .isFalse();
    }

    @Test
    void productionProfile_isRefused_beforeAnythingStarts() {
        for (String production : new String[] {"prod", "PRODUCTION"}) {
            MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE, production);
            assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(() -> {
                        throw new AssertionError("must be refused before the launcher lookup");
                    }).postProcessEnvironment(environment, application()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("interdit en production");
        }
    }

    @Test
    void appEnvironmentProduction_orUnset_isRefused() {
        MockEnvironment unset = new MockEnvironment();
        unset.setActiveProfiles(EmbeddedDbSettings.PROFILE);
        MockEnvironment explicit = environment(EmbeddedDbSettings.PROFILE).withProperty("app.environment", "Production");

        for (MockEnvironment environment : new MockEnvironment[] {unset, explicit}) {
            assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(Optional::empty)
                            .postProcessEnvironment(environment, application()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_ENVIRONMENT");
        }
    }

    @Test
    void stagingEnvironment_isAllowed() {
        MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE).withProperty("app.environment", "staging");

        assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(Optional::empty)
                        .postProcessEnvironment(environment, application()))
                .hasMessageContaining("-Pembedded-db");
    }

    @Test
    void launcherAbsentFromTheJar_failsWithAClearMessage() {
        MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE);

        assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(Optional::empty)
                        .postProcessEnvironment(environment, application()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("-Pembedded-db");
    }

    @Test
    void otherTenantThanTenantDev_isRefused() {
        MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE).withProperty("app.tenant.id", "acme");

        assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(Optional::empty)
                        .postProcessEnvironment(environment, application()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_dev");
    }

    @Test
    void launcherStarted_datasourcePropertiesAreSet_andSettingsComeFromTheEnvironment() {
        MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE)
                .withProperty("app.embedded-db.data-dir", "/var/lib/flowforge-demo")
                .withProperty("app.embedded-db.port", "25432");
        AtomicReference<EmbeddedDbSettings> received = new AtomicReference<>();
        EmbeddedDbLauncher launcher = settings -> {
            received.set(settings);
            return new EmbeddedDbLauncher.RunningDb(
                    "jdbc:postgresql://127.0.0.1:25432/flowforge", "flowforge_tenant_dev", "generated", () -> {});
        };

        new EmbeddedDbEnvironmentPostProcessor(() -> Optional.of(launcher))
                .postProcessEnvironment(environment, application());

        // EmbeddedDbSettings rend le chemin absolu : sous Windows, /var/lib/... devient C:\var\lib\...
        assertThat(received.get().baseDir()).isEqualTo(Path.of("/var/lib/flowforge-demo").toAbsolutePath());
        assertThat(received.get().pgDataDir())
                .isEqualTo(Path.of("/var/lib/flowforge-demo/pgdata").toAbsolutePath());
        assertThat(received.get().port()).isEqualTo(25432);
        assertThat(received.get().database()).isEqualTo("flowforge");
        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://127.0.0.1:25432/flowforge");
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("flowforge_tenant_dev");
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("generated");
    }

    @Test
    void launcherFailure_isWrappedWithItsMessage() {
        MockEnvironment environment = environment(EmbeddedDbSettings.PROFILE);
        EmbeddedDbLauncher failing = settings -> {
            throw new java.io.IOException("répertoire verrouillé");
        };

        assertThatThrownBy(() -> new EmbeddedDbEnvironmentPostProcessor(() -> Optional.of(failing))
                        .postProcessEnvironment(environment, application()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("répertoire verrouillé");
    }
}
