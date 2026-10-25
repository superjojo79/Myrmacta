package be.myrmacta.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class TenantIdEnvironmentValidatorTest {

    private final TenantIdEnvironmentValidator validator = new TenantIdEnvironmentValidator();

    @Test
    void postProcessEnvironment_withValidId_doesNotThrow() {
        StandardEnvironment environment = environmentWith("tenant_dev");

        assertThatCode(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .doesNotThrowAnyException();
    }

    @Test
    void postProcessEnvironment_withInvalidId_throwsInvalidTenantIdException() {
        StandardEnvironment environment = environmentWith("tenant-a");

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(InvalidTenantIdException.class);
    }

    @Test
    void postProcessEnvironment_withMissingId_throwsInvalidTenantIdException() {
        StandardEnvironment environment = new StandardEnvironment();

        assertThatThrownBy(() -> validator.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(InvalidTenantIdException.class);
    }

    /**
     * A dedicated {@link MapPropertySource} on this environment instance only — {@code
     * environment.getSystemProperties()} would mutate the live, shared JVM {@code
     * System.getProperties()}, leaking {@code app.tenant.id} into every other test in the same
     * forked JVM.
     */
    private static StandardEnvironment environmentWith(String tenantId) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources()
                .addFirst(new MapPropertySource("test", Map.of("app.tenant.id", tenantId)));
        return environment;
    }
}
