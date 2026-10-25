package be.myrmacta.infrastructure.embeddeddb;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Supplier;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

/**
 * Profil {@code embedded-db} : démarre un PostgreSQL embarqué <strong>persistant</strong> avant la
 * création du DataSource et fixe {@code spring.datasource.*}. Réservé aux démos/QA : refusé dès
 * qu'un profil de production est actif, et indisponible dans le jar standard (voir
 * {@link EmbeddedDbLauncher}).
 */
public class EmbeddedDbEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "embeddedDb";
    private static final List<String> PRODUCTION_PROFILES = List.of("prod", "production");

    private final Supplier<Optional<EmbeddedDbLauncher>> launcherSupplier;

    public EmbeddedDbEnvironmentPostProcessor() {
        this(() -> ServiceLoader.load(EmbeddedDbLauncher.class).findFirst());
    }

    EmbeddedDbEnvironmentPostProcessor(Supplier<Optional<EmbeddedDbLauncher>> launcherSupplier) {
        this.launcherSupplier = launcherSupplier;
    }

    /**
     * Après le chargement de application.yml / des variables d'environnement, mais avant
     * {@code SecretsEnvironmentPostProcessor} (non ordonné) qui valide le mot de passe datasource.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(EmbeddedDbSettings.PROFILE))
                || environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        refuseInProduction(environment);
        EmbeddedDbSettings settings = EmbeddedDbSettings.from(environment);
        if (!EmbeddedDbSettings.DEFAULT_TENANT.equals(settings.tenantId())) {
            throw new IllegalStateException("Le profil " + EmbeddedDbSettings.PROFILE + " ne gère que le tenant '"
                    + EmbeddedDbSettings.DEFAULT_TENANT + "' (app.tenant.id=" + settings.tenantId()
                    + ") : le schéma et le rôle sont créés par docker/postgres/init/01-init-tenant-schema.sql.");
        }
        EmbeddedDbLauncher launcher = launcherSupplier.get()
                .orElseThrow(() -> new IllegalStateException("Le profil " + EmbeddedDbSettings.PROFILE
                        + " est actif mais ce jar ne contient pas le PostgreSQL embarqué : le construire avec "
                        + "`./mvnw -Pembedded-db package` (jamais fourni dans le jar de production)."));

        EmbeddedDbLauncher.RunningDb db;
        try {
            db = launcher.start(settings);
        } catch (Exception e) {
            throw new IllegalStateException("Démarrage du PostgreSQL embarqué impossible : " + e.getMessage(), e);
        }
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(
                "spring.datasource.url", db.jdbcUrl(),
                "spring.datasource.username", db.username(),
                "spring.datasource.password", db.password(),
                "spring.datasource.driver-class-name", "org.postgresql.Driver")));
        // Singleton enregistré avant tout autre bean => détruit en dernier : la base s'arrête après
        // le DataSource et Flowable (pas de « terminating connection » à l'arrêt).
        application.addInitializers(context -> {
            if (context.getBeanFactory() instanceof DefaultListableBeanFactory factory) {
                factory.registerSingleton("embeddedDbShutdown", db);
                factory.registerDisposableBean("embeddedDbShutdown", () -> {
                    try {
                        db.handle().close();
                    } catch (Exception ignored) {
                        // arrêt best-effort : PostgreSQL gère la reprise sur arrêt brutal
                    }
                });
            }
        });
    }

    static void refuseInProduction(ConfigurableEnvironment environment) {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(PRODUCTION_PROFILES::contains)
                // app.environment : local | staging | production (défaut production, le plus strict).
                || "production".equalsIgnoreCase(environment.getProperty("app.environment", "production").trim());
        if (production) {
            throw new IllegalStateException("Le profil " + EmbeddedDbSettings.PROFILE
                    + " (PostgreSQL embarqué) est interdit en production (profil prod/production, ou app.environment "
                    + "absent / égal à production) : définir APP_ENVIRONMENT=local ou staging pour une démo/QA, "
                    + "sinon utiliser un PostgreSQL géré.");
        }
    }
}
