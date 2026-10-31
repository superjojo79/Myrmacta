package be.myrmacta.infrastructure.persistence.mailroom;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — table {@code dossier_type_reply_workflow} (changeset 044). */
@Component
class JdbcReplyWorkflowSettings implements ReplyWorkflowSettings {

    private final JdbcTemplate jdbc;

    JdbcReplyWorkflowSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ReplyWorkflowSetting> find(String tenantId, String dossierTypeCode) {
        List<ReplyWorkflowSetting> found = jdbc.query("""
                select tenant_id, dossier_type_code, mode, workflow_key, child_type_code from dossier_type_reply_workflow
                where tenant_id = ? and dossier_type_code = ?
                """, (rs, i) -> new ReplyWorkflowSetting(rs.getString("tenant_id"), rs.getString("dossier_type_code"),
                        ReplyWorkflowSetting.Mode.valueOf(rs.getString("mode")), rs.getString("workflow_key"), rs.getString("child_type_code")),
                tenantId, dossierTypeCode);
        return found.stream().findFirst();
    }

    @Override
    public void save(ReplyWorkflowSetting setting) {
        jdbc.update("""
                insert into dossier_type_reply_workflow (tenant_id, dossier_type_code, mode, workflow_key, child_type_code)
                values (?, ?, ?, ?, ?)
                on conflict (tenant_id, dossier_type_code)
                do update set mode = excluded.mode, workflow_key = excluded.workflow_key, child_type_code = excluded.child_type_code
                """, setting.tenantId(), setting.dossierTypeCode(), setting.mode().name(), setting.workflowKey(), setting.childTypeCode());
    }

    @Override
    public void delete(String tenantId, String dossierTypeCode) {
        jdbc.update("delete from dossier_type_reply_workflow where tenant_id = ? and dossier_type_code = ?", tenantId, dossierTypeCode);
    }
}
