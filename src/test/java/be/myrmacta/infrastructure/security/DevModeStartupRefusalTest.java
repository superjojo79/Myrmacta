package be.myrmacta.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/**
 * TODO §95 (P1) : {@link DevModeEnvironmentPostProcessor} est bien enregistré dans la chaîne de démarrage
 * (META-INF/spring/…EnvironmentPostProcessor) — un démarrage réel est refusé avant tout bean applicatif.
 */
class DevModeStartupRefusalTest {

    @Configuration(proxyBeanMethods = false)
    static class Empty {
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(Empty.class).web(WebApplicationType.NONE);
    }

    @Test
    void localProfile_withProductionEnvironment_refusesToStart() {
        assertThatThrownBy(() -> application().profiles("local").run("--app.environment=production").close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_PROFILES_ACTIVE=local requires APP_ENVIRONMENT=local");
    }

    /**
     * {@link SecretsEnvironmentPostProcessor} n'était déclaré que dans un fichier que Spring Boot ne lit pas
     * (META-INF/spring/…EnvironmentPostProcessor) : son refus des secrets par défaut ne s'exécutait jamais.
     */
    @Test
    void prodProfile_withDefaultSecrets_refusesToStart() {
        assertThatThrownBy(() -> application().run("--spring.profiles.active=prod").close())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("outside profiles local/test");
    }
}
