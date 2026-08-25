package com.flowforge.support;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Conteneur PostgreSQL singleton partagé (D-CONTAINER-REUSE, design doc
 * fiabilisation-tests-plateforme) — démarré une seule fois par JVM de test,
 * réutilisé par toutes les classes qui ont réellement besoin d'un moteur
 * PostgreSQL (search_path natif, verrouillage ligne réel).
 *
 * <p>Le schéma applicatif n'est pas créé par du code de production (le
 * bootstrap CREATE SCHEMA + migration décrit dans
 * {@code analysis doc/02-multi-tenant.md} est de la responsabilité de
 * l'infra, pas de l'application) : cette classe le reproduit explicitement
 * pour les besoins du test.
 */
public final class PostgresTestContainerSupport {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private static PostgreSQLContainer<?> container;

    private PostgresTestContainerSupport() {
    }

    /**
     * À vérifier via {@code Assumptions.assumeTrue(...)} en tout premier dans chaque test qui
     * utilise cette classe, avant tout autre appel — Docker est absent dans ce bac à sable
     * (D-TESTCONTAINERS-EXEC) : ces tests doivent alors être proprement ignorés (SKIPPED), pas
     * planter/bloquer, tout en tournant normalement en CI (Docker natif sur {@code ubuntu-latest}).
     */
    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable ex) {
            return false;
        }
    }

    private static synchronized PostgreSQLContainer<?> container() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        return container;
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
     * éviter d'accumuler des pools de connexions non fermés sur le conteneur singleton partagé.
     */
    public static HikariDataSource dataSourceForSchema(String schemaName) {
        PostgreSQLContainer<?> postgres = container();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setConnectionInitSql("SET search_path TO " + schemaName + ", public");
        config.setMaximumPoolSize(5);
        return new HikariDataSource(config);
    }

    public static String jdbcUrl() {
        return container().getJdbcUrl();
    }

    public static String adminUsername() {
        return container().getUsername();
    }

    public static String adminPassword() {
        return container().getPassword();
    }

    private static Connection adminConnection() throws SQLException {
        PostgreSQLContainer<?> postgres = container();
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static Connection schemaConnection(String schemaName) throws SQLException {
        Connection connection = adminConnection();
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO " + statement.enquoteIdentifier(schemaName, true) + ", public");
        }
        return connection;
    }
}
