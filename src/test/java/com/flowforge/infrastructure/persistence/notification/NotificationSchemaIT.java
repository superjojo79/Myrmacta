package com.flowforge.infrastructure.persistence.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class NotificationSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void liquibase_createsNotificationTables() {
        assertThat(tableExists("notification_webhook")).isTrue();
        assertThat(tableExists("notification_outbox")).isTrue();
    }

    @Test
    void notificationWebhook_hasCoreColumns() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE lower(table_name) = 'notification_webhook'
                  AND lower(column_name) IN (
                    'id', 'tenant_id', 'name', 'target_url', 'secret_ciphertext',
                    'enabled', 'deleted_at', 'created_at', 'updated_at')
                """,
                Integer.class);
        assertThat(count).isEqualTo(9);
    }

    @Test
    void notificationOutbox_hasCoreColumns() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE lower(table_name) = 'notification_outbox'
                  AND lower(column_name) IN (
                    'id', 'tenant_id', 'channel', 'status', 'payload_json',
                    'attempts', 'next_attempt_at', 'last_error',
                    'created_at', 'updated_at', 'processed_at')
                """,
                Integer.class);
        assertThat(count).isEqualTo(11);
    }

    @Test
    void notificationOutbox_hasStatusNextAttemptIndex() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE lower(table_name) = 'notification_outbox'
                  AND lower(index_name) = 'ix_notification_outbox_status_next'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void notificationOutbox_hasStatusCreatedIndex() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE lower(table_name) = 'notification_outbox'
                  AND lower(index_name) = 'ix_notification_outbox_status_created_at'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void notificationWebhook_hasActiveNameUniqueIndex() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE lower(table_name) = 'notification_webhook'
                  AND lower(index_name) = 'uq_notification_webhook_tenant_name_active'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
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
