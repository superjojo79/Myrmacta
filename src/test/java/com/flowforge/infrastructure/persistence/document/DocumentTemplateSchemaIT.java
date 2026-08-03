package com.flowforge.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DocumentTemplateSchemaIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void liquibase_createsDocumentTemplateTable() {
        assertThat(tableExists("document_template")).isTrue();
    }

    @Test
    void documentTemplate_hasCoreColumns() {
        Integer count = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.columns
                WHERE lower(table_name) = 'document_template'
                  AND lower(column_name) IN (
                    'id', 'tenant_id', 'template_key', 'variant_key', 'locale',
                    'label', 'version', 'status', 'html_content',
                    'created_at', 'updated_at', 'updated_by')
                """,
                Integer.class);
        assertThat(count).isEqualTo(12);
    }

    @Test
    void uniqueActive_rejectsSecondActiveForSameCombination() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertActive(first, "contrat", null, null, 1);
        assertThatThrownBy(() -> insertActive(second, "contrat", null, null, 2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertActive(UUID id, String key, String variant, String locale, int version) {
        jdbc.update(
                """
                INSERT INTO document_template (
                  id, tenant_id, template_key, variant_key, locale, label, version, status,
                  html_content, created_at, updated_at, updated_by)
                VALUES (?, 'tenant_dev', ?, ?, ?, 'label', ?, 'ACTIVE', '<p>x</p>',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test')
                """,
                id,
                key,
                variant,
                locale,
                version);
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
