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
class DossierRelationSchemaTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void dossierRelation_tableAndCoreColumnsExist() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE lower(table_name) = 'dossier_relation'
                """,
                Integer.class);
        assertThat(count).isGreaterThanOrEqualTo(1);

        List<String> columns = jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = 'dossier_relation'
                """,
                String.class);
        assertThat(columns)
                .contains(
                        "id",
                        "tenant_id",
                        "source_dossier_id",
                        "target_dossier_id",
                        "type",
                        "created_by",
                        "created_at",
                        "idempotency_key");
    }
}
