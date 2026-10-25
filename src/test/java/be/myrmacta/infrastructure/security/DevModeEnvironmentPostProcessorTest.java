package be.myrmacta.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.impl.NoOpLog;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

/** TODO §95 (P1) : un seul interrupteur « mode dev », APP_ENVIRONMENT. */
class DevModeEnvironmentPostProcessorTest {

    private final List<String> warnings = new ArrayList<>();
    private final DevModeEnvironmentPostProcessor processor = new DevModeEnvironmentPostProcessor(destination ->
            new NoOpLog() {
                @Override
                public void warn(Object message) {
                    warnings.add(String.valueOf(message));
                }
            });

    private static MockEnvironment environment(String appEnvironment, String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        if (appEnvironment != null) {
            environment.setProperty("app.environment", appEnvironment);
        }
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private void run(MockEnvironment environment) {
        processor.postProcessEnvironment(environment, new SpringApplication(Object.class));
    }

    @Test
    void localProfile_withLocalEnvironment_isAllowed() {
        assertThatCode(() -> run(environment("local", "local"))).doesNotThrowAnyException();
        assertThatCode(() -> run(environment(" Local ", "LOCAL"))).doesNotThrowAnyException();
    }

    @Test
    void localProfile_outsideLocalEnvironment_isRefused() {
        for (String appEnvironment : new String[] {"staging", "production", null}) {
            assertThatThrownBy(() -> run(environment(appEnvironment, "local")))
                    .as("APP_ENVIRONMENT=%s", appEnvironment)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SPRING_PROFILES_ACTIVE=local")
                    .hasMessageContaining("APP_ENVIRONMENT=local");
        }
    }

    @Test
    void testProfile_isRefusedInProduction_butAllowedInLocalAndStaging() {
        assertThatCode(() -> run(environment("local", "test"))).doesNotThrowAnyException();
        assertThatCode(() -> run(environment("staging", "test"))).doesNotThrowAnyException();
        for (String appEnvironment : new String[] {"production", null}) {
            assertThatThrownBy(() -> run(environment(appEnvironment, "test")))
                    .as("APP_ENVIRONMENT=%s", appEnvironment)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SPRING_PROFILES_ACTIVE=test");
        }
    }

    @Test
    void noopAntivirus_inProduction_isRefused_unlessAcknowledged() {
        MockEnvironment implicit = environment("production", "prod");
        MockEnvironment explicit = environment("production", "prod");
        explicit.setProperty("app.dossier.attachment.scan.type", "NOOP");

        for (MockEnvironment environment : new MockEnvironment[] {implicit, explicit}) {
            assertThatThrownBy(() -> run(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APP_DOSSIER_ATTACHMENT_SCAN_TYPE")
                    .hasMessageContaining("APP_DOSSIER_ATTACHMENT_SCAN_NOOP_ACKNOWLEDGED");
        }

        explicit.setProperty("app.dossier.attachment.scan.noop-acknowledged", "true");
        assertThatCode(() -> run(explicit)).doesNotThrowAnyException();
        assertThat(warnings).singleElement().asString().contains("antivirus");
    }

    @Test
    void clamavAntivirus_inProduction_isAllowedSilently() {
        MockEnvironment environment = environment("production");
        environment.setProperty("app.dossier.attachment.scan.type", "clamav");

        assertThatCode(() -> run(environment)).doesNotThrowAnyException();
        assertThat(warnings).isEmpty();
    }

    @Test
    void noopAntivirus_inStaging_onlyWarns() {
        assertThatCode(() -> run(environment("staging"))).doesNotThrowAnyException();
        assertThat(warnings).singleElement().asString().contains("antivirus").contains("staging");
    }

    @Test
    void noopAntivirus_inLocal_isSilent() {
        assertThatCode(() -> run(environment("local", "local"))).doesNotThrowAnyException();
        assertThat(warnings).isEmpty();
    }
}
