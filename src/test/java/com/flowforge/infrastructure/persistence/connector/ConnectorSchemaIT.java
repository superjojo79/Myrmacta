package com.flowforge.infrastructure.persistence.connector;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ConnectorSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void liquibase_createsConnectorTables() {
        assertThat(tableExists("connector_instance")).isTrue();
        assertThat(tableExists("tenant_connector_dek")).isTrue();
        assertThat(tableExists("webhook_idempotency")).isTrue();
    }

    @Test
    void connectorInstance_hasDeletedAtAndCoreColumns() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'connector_instance'
                  AND column_name IN ('id', 'tenant_id', 'name', 'connector_type', 'enabled', 'config', 'deleted_at')
                """,
                Integer.class);
        assertThat(count).isEqualTo(7);
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_name) = lower(?)
                """,
                Integer.class,
                table);
        return count != null && count > 0;
    }
}
