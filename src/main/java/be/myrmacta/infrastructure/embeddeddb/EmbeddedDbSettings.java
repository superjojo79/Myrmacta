package be.myrmacta.infrastructure.embeddeddb;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.env.Environment;

/**
 * Paramètres du PostgreSQL embarqué persistant.
 *
 * @param baseDir répertoire racine persistant (cluster {@code pgdata/}, secrets, verrou)
 * @param port port TCP d'écoute sur 127.0.0.1 ({@code 0} = port libre aléatoire)
 * @param database nom de la base applicative
 * @param tenantId schéma tenant (doit être celui créé par le script d'initialisation du dépôt)
 */
public record EmbeddedDbSettings(Path baseDir, int port, String database, String tenantId) {

    public static final String PROFILE = "embedded-db";
    static final String DEFAULT_TENANT = "tenant_dev";

    static EmbeddedDbSettings from(Environment environment) {
        String home = environment.getProperty("user.home", ".");
        Path current = Path.of(home, ".myrmacta", "embedded-db");
        Path legacy = Path.of(home, ".flowforge", "embedded-db"); // flowforge-legacy
        String configuredDir = environment.getProperty("app.embedded-db.data-dir");
        boolean useLegacy = false;
        Path baseDir;
        if (configuredDir != null && !configuredDir.isBlank()) {
            baseDir = Path.of(configuredDir);
        } else if (!Files.exists(current) && Files.exists(legacy)) {
            baseDir = legacy;
            useLegacy = true;
        } else {
            baseDir = current;
        }
        int port = environment.getProperty("app.embedded-db.port", Integer.class, 15432);
        String configuredDatabase = environment.getProperty("app.embedded-db.database");
        String database = configuredDatabase == null || configuredDatabase.isBlank()
                ? (useLegacy ? "flowforge" : "myrmacta") // flowforge-legacy
                : configuredDatabase;
        String tenantId = environment.getProperty("app.tenant.id", DEFAULT_TENANT);
        return new EmbeddedDbSettings(baseDir.toAbsolutePath(), port, database, tenantId);
    }

    public Path pgDataDir() {
        return baseDir.resolve("pgdata");
    }
}
