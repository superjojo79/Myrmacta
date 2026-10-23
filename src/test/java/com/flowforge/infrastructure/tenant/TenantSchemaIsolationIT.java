package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.EmbeddedPostgresSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Étanchéité du {@code search_path} par tenant sous un vrai PostgreSQL — fiabilisation-tests-plateforme,
 * D-ZONKY (PostgreSQL embarqué). Reproduit le bootstrap réel (CREATE SCHEMA + migration
 * Liquibase par schéma, {@code docs/specs/02-multi-tenant.md}) sur l'instance PostgreSQL
 * embarquée partagée ({@link EmbeddedPostgresSupport}), puis prouve qu'une connexion dont le search_path est fixé
 * sur le schéma A ne voit jamais les lignes du schéma B.
 */
class TenantSchemaIsolationIT {

    @Test
    void searchPath_isolatesTenantsInSeparateSchemas() throws Exception {
        String schemaA = "tenant_iso_a_" + UUID.randomUUID().toString().replace("-", "");
        String schemaB = "tenant_iso_b_" + UUID.randomUUID().toString().replace("-", "");
        EmbeddedPostgresSupport.createSchema(schemaA);
        EmbeddedPostgresSupport.createSchema(schemaB);
        EmbeddedPostgresSupport.migrateSchema(schemaA);
        EmbeddedPostgresSupport.migrateSchema(schemaB);

        try (HikariDataSource dataSourceA = EmbeddedPostgresSupport.dataSourceForSchema(schemaA);
                HikariDataSource dataSourceB = EmbeddedPostgresSupport.dataSourceForSchema(schemaB)) {
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
            EmbeddedPostgresSupport.dropSchema(schemaA);
            EmbeddedPostgresSupport.dropSchema(schemaB);
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
