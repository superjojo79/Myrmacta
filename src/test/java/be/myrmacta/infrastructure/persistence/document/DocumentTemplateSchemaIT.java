package be.myrmacta.infrastructure.persistence.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

    /**
     * Le contexte (donc la base PostgreSQL) est partagé avec d'autres tests via le cache Spring. Les
     * lignes insérées en SQL brut (html_content en texte) ne sont pas relisibles par l'entité JPA
     * ({@code @Lob String} : sous PostgreSQL Hibernate lit la colonne comme un OID de large object) :
     * on les supprime pour ne pas polluer {@code JpaDocumentTemplateRepositoryAdapterTest}.
     */
    @AfterEach
    void removeRowsInsertedBySqlOnly() {
        jdbc.update("DELETE FROM document_template WHERE template_key LIKE 'uq-%'");
    }

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
        String key = "uq-active-" + UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertRow(first, key, null, null, 1, "ACTIVE");
        assertThatThrownBy(() -> insertRow(second, key, null, null, 2, "ACTIVE"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueVersion_rejectsDuplicateVersionForSameCombination() {
        String key = "uq-ver-" + UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        insertRow(first, key, "premium", "fr", 1, "DRAFT");
        assertThatThrownBy(() -> insertRow(second, key, "premium", "fr", 1, "ARCHIVED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertRow(UUID id, String key, String variant, String locale, int version, String status) {
        jdbc.update(
                """
                INSERT INTO document_template (
                  id, tenant_id, template_key, variant_key, locale, label, version, status,
                  html_content, created_at, updated_at, updated_by)
                VALUES (?, 'tenant_dev', ?, ?, ?, 'label', ?, ?, '<p>x</p>',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'test')
                """,
                id,
                key,
                variant,
                locale,
                version,
                status);
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
