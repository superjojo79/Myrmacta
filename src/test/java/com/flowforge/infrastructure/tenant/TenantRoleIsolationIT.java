package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.support.EmbeddedPostgresSupport;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

/**
 * D-41 : le rôle LOGIN d'un tenant SELECT son schéma et se voit refuser un autre schéma.
 */
class TenantRoleIsolationIT {

    @Test
    void tenantRole_canSelectOwnSchema_butNotAnother() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String schemaA = "tenant_role_a_" + suffix;
        String schemaB = "tenant_role_b_" + suffix;
        String roleA = "role_a_" + suffix;
        String roleB = "role_b_" + suffix;
        String password = "role-pass-" + suffix;

        try (Connection admin = DriverManager.getConnection(
                EmbeddedPostgresSupport.jdbcUrl(),
                EmbeddedPostgresSupport.adminUsername(),
                EmbeddedPostgresSupport.adminPassword());
                Statement statement = admin.createStatement()) {
            createSchemaAndRole(statement, schemaA, roleA, password);
            createSchemaAndRole(statement, schemaB, roleB, password);
        }

        try (Connection asA = DriverManager.getConnection(
                        EmbeddedPostgresSupport.jdbcUrl(), roleA, password);
                Statement statement = asA.createStatement()) {
            statement.execute("SET search_path TO " + schemaA);
            try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM t")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
            assertThatThrownBy(() -> statement.executeQuery("SELECT COUNT(*) FROM " + schemaB + ".t"))
                    .isInstanceOf(PSQLException.class)
                    .hasMessageContaining("permission denied");
        } finally {
            try (Connection admin = DriverManager.getConnection(
                    EmbeddedPostgresSupport.jdbcUrl(),
                    EmbeddedPostgresSupport.adminUsername(),
                    EmbeddedPostgresSupport.adminPassword());
                    Statement statement = admin.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + schemaA + " CASCADE");
                statement.execute("DROP SCHEMA IF EXISTS " + schemaB + " CASCADE");
                statement.execute("DROP ROLE IF EXISTS " + roleA);
                statement.execute("DROP ROLE IF EXISTS " + roleB);
            } catch (SQLException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void createSchemaAndRole(Statement statement, String schema, String role, String password)
            throws SQLException {
        statement.execute("CREATE SCHEMA " + schema);
        statement.execute("CREATE TABLE " + schema + ".t (id int)");
        statement.execute("INSERT INTO " + schema + ".t VALUES (1)");
        statement.execute("CREATE ROLE " + role + " LOGIN PASSWORD '" + password + "'");
        statement.execute("GRANT USAGE ON SCHEMA " + schema + " TO " + role);
        statement.execute("GRANT SELECT ON " + schema + ".t TO " + role);
        statement.execute("REVOKE ALL ON SCHEMA " + schema + " FROM PUBLIC");
    }
}
