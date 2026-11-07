package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DayCount;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §115 (D-DEADLINE-FIELD) — table {@code dossier_type_deadline} (changeset 045). */
@Component
class JdbcDeadlineSettings implements DeadlineSettings {

    private static final String COLUMNS = "tenant_id, dossier_type_code, field_name, reminder_days, escalation_group, reminder_day_count";

    private final JdbcTemplate jdbc;

    JdbcDeadlineSettings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DeadlineSetting> find(String tenantId, String dossierTypeCode) {
        return jdbc.query("select " + COLUMNS + " from dossier_type_deadline where tenant_id = ? and dossier_type_code = ?",
                (rs, i) -> map(rs), tenantId, dossierTypeCode).stream().findFirst();
    }

    @Override
    public List<DeadlineSetting> findAll(String tenantId) {
        return jdbc.query("select " + COLUMNS + " from dossier_type_deadline where tenant_id = ? order by dossier_type_code",
                (rs, i) -> map(rs), tenantId);
    }

    @Override
    public void save(DeadlineSetting setting) {
        jdbc.update("""
                insert into dossier_type_deadline (tenant_id, dossier_type_code, field_name, reminder_days, escalation_group, reminder_day_count)
                values (?, ?, ?, ?, ?, ?)
                on conflict (tenant_id, dossier_type_code)
                do update set field_name = excluded.field_name, reminder_days = excluded.reminder_days,
                              escalation_group = excluded.escalation_group, reminder_day_count = excluded.reminder_day_count
                """, setting.tenantId(), setting.dossierTypeCode(), setting.fieldName(), setting.reminderDays(),
                setting.escalationGroup(), setting.reminderDayCount().name());
    }

    @Override
    public void delete(String tenantId, String dossierTypeCode) {
        jdbc.update("delete from dossier_type_deadline where tenant_id = ? and dossier_type_code = ?", tenantId, dossierTypeCode);
    }

    private static DeadlineSetting map(ResultSet rs) throws SQLException {
        return new DeadlineSetting(rs.getString("tenant_id"), rs.getString("dossier_type_code"), rs.getString("field_name"),
                rs.getInt("reminder_days"), rs.getString("escalation_group"), DayCount.parse(rs.getString("reminder_day_count")));
    }
}
