package be.myrmacta.infrastructure.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fail-fast hors profils {@code local}/{@code test} : secrets défaut et HMAC M2M (D-43 / S-4).
 */
public class SecretsEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final Set<String> DENIED_PASSWORDS = Set.of("flowforge", "postgres", "admin", "password");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isRelaxed(environment)) {
            return;
        }
        String password = nullToEmpty(environment.getProperty("spring.datasource.password")).trim();
        if (password.isBlank() || isDeniedPassword(password)) {
            throw new IllegalStateException(
                    "DB_PASSWORD must be a non-default value outside profiles local/test");
        }
        String masterKey = nullToEmpty(environment.getProperty("app.connector.crypto.master-key"));
        if (masterKey.isBlank()) {
            throw new IllegalStateException(
                    "APP_CONNECTOR_MASTER_KEY is required outside profiles local/test");
        }
        String jwtSecret = nullToEmpty(environment.getProperty("app.security.m2m.jwt-secret"));
        if (!jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "M2M HMAC jwt-secret (M2M_JWT_SECRET) is forbidden outside profiles local/test");
        }
        String oidcSecret = nullToEmpty(environment.getProperty(
                "spring.security.oauth2.client.registration.oidc.client-secret"));
        if (oidcSecret.isBlank()) {
            throw new IllegalStateException(
                    "OIDC_CLIENT_SECRET is required outside profiles local/test");
        }
    }

    private static boolean isDeniedPassword(String password) {
        String normalized = password.toLowerCase(Locale.ROOT);
        return DENIED_PASSWORDS.contains(normalized);
    }

    private static boolean isRelaxed(ConfigurableEnvironment environment) {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("local") || profile.equals("test"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
