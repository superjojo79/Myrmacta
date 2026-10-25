package be.myrmacta.support;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * PostgreSQL réel pour les tests — remplace H2 (émulation) et Testcontainers (Docker requis).
 *
 * <p>Une instance zonky ({@code embedded-postgres}, binaires PostgreSQL 17 alignés sur
 * docker-compose) est démarrée une seule fois par JVM de test et réutilisée par tous les tests.
 * Chaque contexte Spring obtient sa propre <em>base</em> ({@link #newApplicationDatabase()}),
 * initialisée avec le script réel {@code docker/postgres/init/01-init-tenant-schema.sql} : schéma
 * {@code tenant_dev} + rôle applicatif non-superuser, comme en production.
 *
 * <p>Pour utiliser un PostgreSQL externe à la place (poste sans binaires embarqués, par exemple
 * lorsque les tests tournent sous root, qu'initdb refuse), définir {@code FLOWFORGE_TEST_PG_URL}
 * ({@code jdbc:postgresql://hôte:port/base}), {@code FLOWFORGE_TEST_PG_USER} et {@code FLOWFORGE_TEST_PG_PASSWORD}
 * (anciens noms {@code TEST_PG_*} acceptés) :
 * le rôle doit pouvoir {@code CREATE DATABASE} et {@code CREATE ROLE}.
 *
 * <p>Le schéma applicatif n'est pas créé par du code de production (le bootstrap décrit dans
 * {@code docs/specs/02-multi-tenant.md} est de la responsabilité de l'infra) : cette classe le
 * reproduit explicitement pour les besoins du test.
 */
public final class EmbeddedPostgresSupport {

    public static final String APP_ROLE = "flowforge_tenant_dev";
    public static final String APP_PASSWORD = "flowforge";

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final Path INIT_SQL = Path.of("docker/postgres/init/01-init-tenant-schema.sql");

    /** Connexion JDBC prête à l'emploi vers une base fraîche initialisée comme en production. */
    public record ApplicationDatabase(String jdbcUrl, String username, String password) {}

    private record Server(String host, int port, String adminDatabase, String adminUser, String adminPassword) {
        String urlFor(String database) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + database;
        }
    }

    private static Server server;
    private static EmbeddedPostgres embedded;

    private EmbeddedPostgresSupport() {
    }

    private static synchronized Server server() {
        if (server == null) {
            String externalUrl = env("FLOWFORGE_TEST_PG_URL", "TEST_PG_URL", null);
            server = externalUrl != null && !externalUrl.isBlank() ? external(externalUrl) : startEmbedded();
        }
        return server;
    }

    private static Server startEmbedded() {
        try {
            embedded = EmbeddedPostgres.builder()
                    // Nombreux contextes Spring en cache, chacun avec son pool : large marge.
                    .setServerConfig("max_connections", "800")
                    // Données jetables : durabilité inutile, démarrage et DDL plus rapides.
                    .setServerConfig("fsync", "off")
                    .setServerConfig("synchronous_commit", "off")
                    .setServerConfig("full_page_writes", "off")
                    .start();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException(
                    "Impossible de démarrer PostgreSQL embarqué (zonky). Si les tests tournent sous root, "
                            + "initdb refuse de démarrer : les lancer avec un utilisateur non-root, ou fournir "
                            + "FLOWFORGE_TEST_PG_URL / _USER / _PASSWORD (voir docs/guides/GETTING-STARTED.md).",
                    e);
        }
        // Arrêt : zonky enregistre son propre shutdown hook et supprime le répertoire de données.
        return new Server("localhost", embedded.getPort(), "postgres", "postgres", "postgres");
    }

    private static String env(String name, String legacyName, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            value = System.getenv(legacyName);
        }
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    private static Server external(String jdbcUrl) {
        // jdbc:postgresql://hôte:port/base
        java.net.URI uri = java.net.URI.create(jdbcUrl.substring("jdbc:".length()));
        int port = uri.getPort() > 0 ? uri.getPort() : 5432;
        String database = uri.getPath() == null || uri.getPath().length() < 2 ? "postgres" : uri.getPath().substring(1);
        String user = env("FLOWFORGE_TEST_PG_USER", "TEST_PG_USER", "postgres");
        String password = env("FLOWFORGE_TEST_PG_PASSWORD", "TEST_PG_PASSWORD", "");
        return new Server(uri.getHost(), port, database, user, password);
    }

    /**
     * Crée une base fraîche, y exécute le script d'initialisation réel du dépôt (schéma tenant +
     * rôle applicatif) et renvoie les coordonnées de connexion du rôle applicatif.
     */
    public static ApplicationDatabase newApplicationDatabase() {
        Server s = server();
        String database = "ff_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + statement.enquoteIdentifier(database, true));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create database " + database, e);
        }
        try (Connection connection = DriverManager.getConnection(
                        s.urlFor(database), s.adminUser(), s.adminPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(INIT_SQL, StandardCharsets.UTF_8));
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Cannot initialise database " + database + " from " + INIT_SQL, e);
        }
        return new ApplicationDatabase(s.urlFor(database), APP_ROLE, APP_PASSWORD);
    }

    public static void createSchema(String schemaName) {
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + statement.enquoteIdentifier(schemaName, true));
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot create schema " + schemaName, e);
        }
    }

    public static void dropSchema(String schemaName) {
        try (Connection connection = adminConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + statement.enquoteIdentifier(schemaName, true) + " CASCADE");
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot drop schema " + schemaName, e);
        }
    }

    /** Migre le schéma via le changelog applicatif réel (parité avec la prod). */
    public static void migrateSchema(String schemaName) {
        try (Connection connection = schemaConnection(schemaName)) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(schemaName);
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
            liquibase.update();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot migrate schema " + schemaName, e);
        }
    }

    /**
     * DataSource dont chaque connexion fixe search_path sur ce schéma — mirroir de
     * {@code application.yml}. Type concret {@link HikariDataSource} (pas {@code DataSource})
     * pour que l'appelant puisse la fermer explicitement ({@link HikariDataSource#close()}) et
     * éviter d'accumuler des pools de connexions non fermés sur l'instance partagée.
     */
    public static HikariDataSource dataSourceForSchema(String schemaName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl());
        config.setUsername(adminUsername());
        config.setPassword(adminPassword());
        config.setConnectionInitSql("SET search_path TO " + schemaName + ", public");
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    public static String jdbcUrl() {
        Server s = server();
        return s.urlFor(s.adminDatabase());
    }

    public static String adminUsername() {
        return server().adminUser();
    }

    public static String adminPassword() {
        return server().adminPassword();
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), adminUsername(), adminPassword());
    }

    private static Connection schemaConnection(String schemaName) throws SQLException {
        Connection connection = adminConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + statement.enquoteIdentifier(schemaName, true) + ", public");
        }
        return connection;
    }
}
