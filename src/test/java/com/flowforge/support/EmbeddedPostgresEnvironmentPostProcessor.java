package com.flowforge.support;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Branche chaque contexte Spring de test sur sa propre base PostgreSQL (zonky) : le datasource
 * des tests n'est plus déclaré dans {@code application.properties} mais calculé ici, une base
 * fraîche par contexte (voir {@link EmbeddedPostgresSupport#newApplicationDatabase()}). Désactivable
 * par {@code test.embedded-postgres.enabled=false} pour un test qui fournit son propre datasource.
 */
public class EmbeddedPostgresEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "embeddedPostgresTest";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("test.embedded-postgres.enabled", Boolean.class, true)
                || environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        EmbeddedPostgresSupport.ApplicationDatabase database = EmbeddedPostgresSupport.newApplicationDatabase();
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                "spring.datasource.url", database.jdbcUrl(),
                "spring.datasource.username", database.username(),
                "spring.datasource.password", database.password(),
                "spring.datasource.driver-class-name", "org.postgresql.Driver")));
    }
}
