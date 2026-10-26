package be.myrmacta.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class SecretsEnvironmentPostProcessorTest {

    private final SecretsEnvironmentPostProcessor processor = new SecretsEnvironmentPostProcessor();
    private final SpringApplication application = new SpringApplication();

    @Test
    void testProfile_allowsDemoSecretsAndHmac() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");
        environment.setProperty("spring.datasource.password", "myrmacta");
        environment.setProperty("app.security.m2m.jwt-secret", "test-hmac");
        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void localProfile_allowsDemoSecrets() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        environment.setProperty("spring.datasource.password", "myrmacta");
        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void prodProfile_isNotRelaxedAndRejectsDefaultDbPassword() {
        MockEnvironment environment = prodLike();
        environment.setActiveProfiles("prod");
        environment.setProperty("spring.datasource.password", "myrmacta");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void noProfile_rejectsDefaultDbPassword() {
        MockEnvironment environment = prodLike();
        environment.setProperty("spring.datasource.password", "myrmacta");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void noProfile_rejectsHmacM2m() {
        MockEnvironment environment = prodLike();
        environment.setProperty("app.security.m2m.jwt-secret", "still-an-hmac");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HMAC");
    }

    @Test
    void noProfile_rejectsMissingMasterKey() {
        MockEnvironment environment = prodLike();
        environment.setProperty("app.connector.crypto.master-key", "");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CONNECTOR_MASTER_KEY");
    }

    @Test
    void noProfile_acceptsNonDefaultSecretsWithoutHmac() {
        MockEnvironment environment = prodLike();
        assertThatCode(() -> processor.postProcessEnvironment(environment, application))
                .doesNotThrowAnyException();
    }

    @Test
    void noProfile_rejectsDeniedPasswordIgnoringCaseAndWhitespace() {
        MockEnvironment environment = prodLike();
        environment.setProperty("spring.datasource.password", " Myrmacta ");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void noProfile_rejectsDeniedAliases() {
        for (String denied : new String[] {"postgres", "Admin", "PASSWORD", "flowforge"}) { // flowforge-legacy
            MockEnvironment environment = prodLike();
            environment.setProperty("spring.datasource.password", denied);
            assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DB_PASSWORD");
        }
    }

    @Test
    void noProfile_rejectsBlankDbPassword() {
        MockEnvironment environment = prodLike();
        environment.setProperty("spring.datasource.password", "  ");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    void noProfile_rejectsMissingOidcSecret() {
        MockEnvironment environment = prodLike();
        environment.setProperty("spring.security.oauth2.client.registration.oidc.client-secret", "");
        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, application))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OIDC_CLIENT_SECRET");
    }

    private static MockEnvironment prodLike() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.datasource.password", "not-a-demo-password");
        environment.setProperty("app.connector.crypto.master-key", "dGVzdC1tYXN0ZXIta2V5LTEyMzQ1Ng==");
        environment.setProperty("spring.security.oauth2.client.registration.oidc.client-secret", "oidc-secret");
        return environment;
    }
}
