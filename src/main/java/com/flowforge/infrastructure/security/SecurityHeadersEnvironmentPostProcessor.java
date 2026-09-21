package com.flowforge.infrastructure.security;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast hors profils {@code local}/{@code test} si le paquet d'en-têtes A4 (CSP / HSTS /
 * XFO / nosniff / Referrer-Policy) est désactivé (revue adversariale post-merge PR #128,
 * TODO §64) -- même doctrine que {@link SecretsEnvironmentPostProcessor} (D-43) : un override
 * d'environnement mal aiguillé (héritage d'un `.env` de démo, valeur mal recopiée) ne doit pas
 * pouvoir couper silencieusement toute la défense en profondeur en production.
 */
public class SecurityHeadersEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isRelaxed(environment)) {
            return;
        }
        String enabled = environment.getProperty("app.security.headers.enabled", "true").trim();
        if ("false".equalsIgnoreCase(enabled)) {
            throw new IllegalStateException(
                    "APP_SECURITY_HEADERS_ENABLED=false is forbidden outside profiles local/test");
        }
    }

    private static boolean isRelaxed(ConfigurableEnvironment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("test"));
    }
}
