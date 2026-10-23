package com.flowforge.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.flowforge.support.EmbeddedPostgresSupport;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Migration Liquibase mono-schéma en échec partiel + reprise idempotente au redémarrage —
 * fiabilisation-tests-plateforme, D-LIQUIBASE-TEST. L'architecture réelle est « un schéma par
 * instance, migré une fois au démarrage » ({@code docs/specs/02-multi-tenant.md}), pas un job
 * migrant plusieurs schémas partagés — ce test exerce donc l'échec partiel sur UN schéma, puis la
 * reprise via le suivi natif Liquibase ({@code DATABASECHANGELOG}), exactement comme au
 * redémarrage réel d'une instance après correction du changeset fautif.
 *
 * <p>Exécuté sur le PostgreSQL embarqué ({@link EmbeddedPostgresSupport}), une base fraîche par
 * test : en PostgreSQL le changeset fautif est rejoué dans sa propre transaction, comme en production.
 */
class LiquibasePartialFailureIT {

    private static final String BROKEN_CHANGELOG = "db/changelog/failure-injection/changelog-failure.yaml";
    private static final String FIXED_CHANGELOG = "db/changelog/failure-injection/changelog-failure-fixed.yaml";

    private EmbeddedPostgresSupport.ApplicationDatabase database;

    @BeforeEach
    void freshDatabase() {
        database = EmbeddedPostgresSupport.newApplicationDatabase();
    }

    @Test
    void partialFailure_commitsEarlierChangesetsAndLeavesFailedOneUntracked() throws Exception {
        assertThatThrownBy(() -> migrate(BROKEN_CHANGELOG)).isInstanceOf(LiquibaseException.class);

        try (Connection connection = openConnection()) {
            assertThat(rowCount(connection, "failure_injection_probe")).isEqualTo(1);
            assertThat(changesetApplied(connection, "001-create-probe-table")).isTrue();
            assertThat(changesetApplied(connection, "002-insert-first-row")).isTrue();
            assertThat(changesetApplied(connection, "003-fails-on-not-null"))
                    .as("un changeset qui échoue n'est jamais enregistré comme appliqué")
                    .isFalse();
            assertThat(changesetApplied(connection, "004-never-reached"))
                    .as("les changesets suivant un échec ne sont jamais exécutés")
                    .isFalse();
        }
    }

    @Test
    void restartAfterFix_resumesFromTheFailedChangesetAndCompletes() throws Exception {
        assertThatThrownBy(() -> migrate(BROKEN_CHANGELOG)).isInstanceOf(LiquibaseException.class);

        // "Redémarrage" avec le changeset 003 corrigé : 001/002 déjà trackés ne sont pas rejoués.
        migrate(FIXED_CHANGELOG);

        try (Connection connection = openConnection()) {
            assertThat(rowCount(connection, "failure_injection_probe")).isEqualTo(3);
            assertThat(changesetApplied(connection, "003-fails-on-not-null")).isTrue();
            assertThat(changesetApplied(connection, "004-never-reached")).isTrue();
        }
    }

    private void migrate(String changelogClasspath) throws Exception {
        try (Connection connection = openConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(changelogClasspath, new ClassLoaderResourceAccessor(), database).update();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                database.jdbcUrl() + "?currentSchema=tenant_dev", database.username(), database.password());
    }

    private static int rowCount(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static boolean changesetApplied(Connection connection, String id) throws SQLException {
        try (var preparedStatement = connection.prepareStatement(
                "SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID = ?")) {
            preparedStatement.setString(1, id);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }
}
