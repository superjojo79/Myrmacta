package be.myrmacta.infrastructure.persistence.workflow;

import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §113 (D-GROUP-LINK) — table {@code workflow_link_group} (changeset 042). */
@Component
class JdbcWorkflowLinkGroupRepository implements WorkflowLinkGroupRepository {

    private final JdbcTemplate jdbc;

    JdbcWorkflowLinkGroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void assign(String tenantId, UUID linkId, String groupCode) {
        jdbc.update("""
                insert into workflow_link_group (link_id, tenant_id, group_code) values (?, ?, ?)
                on conflict (link_id) do update set group_code = excluded.group_code
                """, linkId, tenantId, groupCode);
    }

    @Override
    public Optional<String> findGroup(UUID linkId) {
        List<String> found = jdbc.queryForList("select group_code from workflow_link_group where link_id = ?", String.class, linkId);
        return found.stream().findFirst();
    }
}
