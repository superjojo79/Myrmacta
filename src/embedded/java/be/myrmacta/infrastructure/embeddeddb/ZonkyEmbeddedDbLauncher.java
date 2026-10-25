package be.myrmacta.infrastructure.embeddeddb;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Implémentation zonky du PostgreSQL embarqué persistant (démos/QA). Compilée et empaquetée
 * uniquement par le profil Maven {@code embedded-db}.
 *
 * <p>Disposition de {@link EmbeddedDbSettings#baseDir()} : {@code pgdata/} (cluster PostgreSQL),
 * {@code app-role-password} (mot de passe généré du rôle applicatif, 0600), {@code embedded-db.lock}
 * (verrou d'instance unique).
 *
 * <p>Sécurité : le serveur n'écoute que sur 127.0.0.1 mais zonky initialise le cluster en
 * authentification {@code trust} : tout utilisateur de la machine peut s'y connecter. À réserver à
 * une machine/conteneur de démo ou de QA non partagé.
 */
public class ZonkyEmbeddedDbLauncher implements EmbeddedDbLauncher {

    /** Version majeure des binaires (BOM zonky) — verrouillée par {@code ZonkyEmbeddedDbLauncherTest}. */
    static final int PG_MAJOR = 17;

    static final String APP_ROLE = "flowforge_tenant_dev";
    private static final String INIT_SQL_RESOURCE = "/db/embedded/01-init-tenant-schema.sql";
    private static final String SUPERUSER = "postgres";
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @Override
    public RunningDb start(EmbeddedDbSettings settings) throws Exception {
        Files.createDirectories(settings.baseDir());
        FileChannel lockChannel = FileChannel.open(
                settings.baseDir().resolve("embedded-db.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            acquire(lockChannel, settings);
            checkMajorVersion(settings.pgDataDir());
            checkNoLiveOrphan(settings.pgDataDir());

            EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                    .setDataDirectory(settings.pgDataDir())
                    .setCleanDataDirectory(false)
                    .setPort(settings.port())
                    // Arrêt piloté par Spring (après le DataSource), pas par un hook concurrent.
                    .setRegisterShutdownHook(false)
                    .setServerConfig("listen_addresses", "127.0.0.1")
                    .setPGStartupWait(Duration.ofSeconds(60))
                    .start();
            try {
                String password = readOrCreatePassword(settings.baseDir().resolve("app-role-password"));
                String url = "jdbc:postgresql://127.0.0.1:" + postgres.getPort() + "/" + settings.database();
                bootstrap(postgres.getPort(), settings.database(), password);
                return new RunningDb(url, APP_ROLE, password, () -> {
                    try {
                        postgres.close();
                    } finally {
                        lockChannel.close();
                    }
                });
            } catch (Exception e) {
                postgres.close();
                throw e;
            }
        } catch (Exception e) {
            lockChannel.close();
            throw e;
        }
    }

    private static void acquire(FileChannel channel, EmbeddedDbSettings settings) throws IOException {
        FileLock lock;
        try {
            lock = channel.tryLock();
        } catch (OverlappingFileLockException sameJvm) {
            lock = null;
        }
        if (lock == null) {
            throw new IllegalStateException("Une autre instance utilise déjà " + settings.baseDir()
                    + " : arrêter l'autre application, ou choisir un autre APP_EMBEDDED_DB_DATA_DIR.");
        }
    }

    /** Refuse un cluster initialisé par une autre version majeure (format de données incompatible). */
    static void checkMajorVersion(Path pgDataDir) throws IOException {
        Path versionFile = pgDataDir.resolve("PG_VERSION");
        if (!Files.exists(versionFile)) {
            return;
        }
        String found = Files.readString(versionFile, StandardCharsets.UTF_8).trim();
        if (!String.valueOf(PG_MAJOR).equals(found)) {
            throw new IllegalStateException("Le répertoire " + pgDataDir + " a été initialisé par PostgreSQL " + found
                    + " alors que ce build embarque PostgreSQL " + PG_MAJOR + " : restaurer une sauvegarde pg_dump "
                    + "dans un répertoire neuf, ou réinitialiser (scripts/embedded-db.sh reset).");
        }
    }

    /** Un postmaster survivant à un arrêt brutal de l'application bloquerait le répertoire de données. */
    static void checkNoLiveOrphan(Path pgDataDir) throws IOException {
        Path pidFile = pgDataDir.resolve("postmaster.pid");
        if (!Files.exists(pidFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(pidFile, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return;
        }
        long pid;
        try {
            pid = Long.parseLong(lines.getFirst().trim());
        } catch (NumberFormatException e) {
            return;
        }
        Optional<ProcessHandle> live = ProcessHandle.of(pid)
                .filter(handle -> handle.info().command().map(c -> c.contains("postgres")).orElse(false));
        if (live.isPresent()) {
            throw new IllegalStateException("Un PostgreSQL (pid " + pid + ") utilise déjà " + pgDataDir
                    + " — arrêt brutal précédent de l'application ? L'arrêter (kill " + pid + ") puis relancer.");
        }
    }

    private static void bootstrap(int port, String database, String appPassword) throws SQLException, IOException {
        try (Connection admin = connect(port, "postgres");
                Statement statement = admin.createStatement()) {
            boolean exists;
            try (ResultSet rs = statement.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '" + database.replace("'", "''") + "'")) {
                exists = rs.next();
            }
            if (!exists) {
                statement.execute("CREATE DATABASE " + statement.enquoteIdentifier(database, true));
            }
        }
        String initSql;
        try (var in = ZonkyEmbeddedDbLauncher.class.getResourceAsStream(INIT_SQL_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(INIT_SQL_RESOURCE + " introuvable : build sans le profil embedded-db ?");
            }
            initSql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection admin = connect(port, database);
                Statement statement = admin.createStatement()) {
            // Même script que docker-compose (idempotent) : schéma tenant_dev + rôle applicatif.
            statement.execute(initSql);
            // Le script crée le rôle avec son mot de passe de démo ; on le remplace par le secret généré.
            statement.execute("ALTER ROLE " + APP_ROLE + " PASSWORD '" + appPassword + "'");
        }
    }

    private static Connection connect(int port, String database) throws SQLException {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/" + database, SUPERUSER, "");
    }

    private static String readOrCreatePassword(Path file) throws IOException {
        if (Files.exists(file)) {
            return Files.readString(file, StandardCharsets.UTF_8).trim();
        }
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            password.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        Files.writeString(file, password, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException notPosix) {
            // Windows : permissions héritées du répertoire
        }
        return password.toString();
    }
}
