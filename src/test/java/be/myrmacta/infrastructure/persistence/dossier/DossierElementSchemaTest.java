package be.myrmacta.infrastructure.persistence.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.OidcClientTestConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DossierElementSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dossierElement_tableExists() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_name) = 'dossier_element'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dossierElement_hasCoreColumns() {
        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'dossier_element'
                """,
                String.class);
        assertThat(columns)
                .contains(
                        "id",
                        "dossier_id",
                        "tenant_id",
                        "type",
                        "label",
                        "storage_ref",
                        "metadata",
                        "added_by",
                        "added_at",
                        "workflow_step_id");
    }
}
