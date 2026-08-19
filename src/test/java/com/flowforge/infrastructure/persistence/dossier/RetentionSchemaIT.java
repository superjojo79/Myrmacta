package com.flowforge.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class RetentionSchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenantRetentionSettings_hasCoreColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'tenant_retention_settings'
                """,
                String.class);
        assertThat(columns)
                .contains("tenant_id", "default_retention_days", "updated_at", "updated_by");
    }

    @Test
    void dossierType_hasRetentionColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'dossier_type'
                """,
                String.class);
        assertThat(columns).contains("retention_days", "retention_never");
    }

    @Test
    void dossier_hasPurgeEligibleAt() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'dossier'
                """,
                String.class);
        assertThat(columns).contains("purge_eligible_at");
    }

    @Test
    void purgeOutbox_tableAndUniqueDossierId() {
        Integer tables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_name) = 'purge_outbox'
                """,
                Integer.class);
        assertThat(tables).isGreaterThanOrEqualTo(1);

        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'purge_outbox'
                """,
                String.class);
        assertThat(columns)
                .contains("id", "dossier_id", "event_type", "status", "payload_json", "created_at", "origin", "last_error");

        Integer uniqueConstraints = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE lower(table_name) = 'purge_outbox'
                  AND constraint_type = 'UNIQUE'
                """,
                Integer.class);
        assertThat(uniqueConstraints).isGreaterThanOrEqualTo(1);
    }

    @Test
    void purgeOutbox_hasStatusCreatedIndex() {
        Integer indexes = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE lower(table_name) = 'purge_outbox'
                  AND lower(index_name) = 'idx_purge_outbox_status_created_at'
                """,
                Integer.class);
        assertThat(indexes).isGreaterThanOrEqualTo(1);
    }
}
