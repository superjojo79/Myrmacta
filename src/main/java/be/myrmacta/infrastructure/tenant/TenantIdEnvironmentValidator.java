package be.myrmacta.infrastructure.tenant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast sur {@code app.tenant.id} avant toute création de bean. Un {@link EnvironmentPostProcessor}
 * s'exécute pendant la préparation de l'{@code Environment}, avant le {@code DataSource}/Liquibase —
 * contrairement à l'ancien {@code ApplicationRunner}, qui validait après que le pool de connexions
 * ait potentiellement déjà été ouvert avec un identifiant non conforme.
 */
public class TenantIdEnvironmentValidator implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        TenantIdValidator.validate(environment.getProperty("app.tenant.id"));
    }
}
