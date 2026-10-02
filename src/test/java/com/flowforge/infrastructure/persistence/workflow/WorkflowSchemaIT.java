package com.flowforge.infrastructure.persistence.workflow;

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
class WorkflowSchemaIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void workflowVisualizationColumnsExist() {
        List<String> workflowTypeConfigColumns = columns("workflow_type_config");
        assertThat(workflowTypeConfigColumns).contains("observer_user_ids");

        List<String> linkColumns = columns("dossier_workflow_link");
        assertThat(linkColumns).contains("started_by");
    }

    @Test
    void processInstanceLookupIndexExists() {
        List<String> indexes = jdbcTemplate.queryForList(
                """
                SELECT lower(indexname) FROM pg_indexes
                WHERE schemaname = current_schema() AND lower(tablename) = 'dossier_workflow_link'
                """,
                String.class);

        assertThat(indexes).contains("ix_dossier_workflow_link_tenant_piid");
    }

    private List<String> columns(String tableName) {
        return jdbcTemplate.queryForList(
                """
                SELECT lower(column_name) FROM information_schema.columns
                WHERE lower(table_name) = ?
                """,
                String.class,
                tableName);
    }
}
