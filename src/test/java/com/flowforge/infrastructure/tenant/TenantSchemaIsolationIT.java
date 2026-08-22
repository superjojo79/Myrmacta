package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.PostgresTestContainerSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Étanchéité du {@code search_path} par tenant sous un vrai PostgreSQL — fiabilisation-tests-plateforme,
 * D-TESTCONTAINERS (adoption ciblée). Reproduit le bootstrap réel (CREATE SCHEMA + migration
 * Liquibase par schéma, {@code analysis doc/02-multi-tenant.md}) sur un conteneur Postgres
 * singleton partagé (D-CONTAINER-REUSE), puis prouve qu'une connexion dont le search_path est fixé
 * sur le schéma A ne voit jamais les lignes du schéma B.
 *
 * <p><strong>⚠️ Non exécuté dans ce bac à sable</strong> (Docker absent, D-TESTCONTAINERS-EXEC) —
 * écrit et compilé ; exécution/vérification à faire en CI (GitHub Actions {@code ubuntu-latest},
 * Docker natif).
 */
class TenantSchemaIsolationIT {

    @Test
    void searchPath_isolatesTenantsInSeparateSchemas() throws Exception {
        Assumptions.assumeTrue(
                PostgresTestContainerSupport.isDockerAvailable(),
                "Docker indisponible dans ce bac à sable (D-TESTCONTAINERS-EXEC) — exécuté en CI (ubuntu-latest, Docker natif)");

        String schemaA = "tenant_iso_a_" + UUID.randomUUID().toString().replace("-", "");
        String schemaB = "tenant_iso_b_" + UUID.randomUUID().toString().replace("-", "");
        PostgresTestContainerSupport.createSchema(schemaA);
        PostgresTestContainerSupport.createSchema(schemaB);
        PostgresTestContainerSupport.migrateSchema(schemaA);
        PostgresTestContainerSupport.migrateSchema(schemaB);

        try {
            DataSource dataSourceA = PostgresTestContainerSupport.dataSourceForSchema(schemaA);
            DataSource dataSourceB = PostgresTestContainerSupport.dataSourceForSchema(schemaB);

            UUID dossierInA = insertDossier(dataSourceA, "DOS-ISO-A-1");
            UUID dossierInB = insertDossier(dataSourceB, "DOS-ISO-B-1");

            assertThat(countDossiers(dataSourceA)).isEqualTo(1);
            assertThat(countDossiers(dataSourceB)).isEqualTo(1);
            assertThat(referenceOf(dataSourceA, dossierInA)).isEqualTo("DOS-ISO-A-1");
            assertThat(referenceOf(dataSourceA, dossierInB))
                    .as("le schéma A ne doit jamais voir un dossier créé dans le schéma B")
                    .isNull();
            assertThat(referenceOf(dataSourceB, dossierInA))
                    .as("le schéma B ne doit jamais voir un dossier créé dans le schéma A")
                    .isNull();
        } finally {
            PostgresTestContainerSupport.dropSchema(schemaA);
            PostgresTestContainerSupport.dropSchema(schemaB);
        }
    }

    private static UUID insertDossier(DataSource dataSource, String reference) throws Exception {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO dossier "
                                + "(id, tenant_id, reference, dossier_type_code, label, status, fields, "
                                + "version, created_by, created_at) "
                                + "VALUES (?, 'iso-test-tenant', ?, 'TEST', 'Isolation test dossier', "
                                + "'OUVERT', '{}'::json, 0, 'tester', now())")) {
            statement.setObject(1, id);
            statement.setString(2, reference);
            statement.executeUpdate();
        }
        return id;
    }

    private static int countDossiers(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM dossier");
                ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static String referenceOf(DataSource dataSource, UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT reference FROM dossier WHERE id = ?")) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString(1) : null;
            }
        }
    }
}
