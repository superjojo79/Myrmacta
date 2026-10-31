package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DeadlineLevel;
import be.myrmacta.domain.deadline.port.out.DeadlineAlertClaims;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** TODO §115 (D-DEADLINE-ESCALATE) — table {@code dossier_deadline_alert} : l'insertion est la prise, la clé primaire l'arbitre. */
@Component
class JdbcDeadlineAlertClaims implements DeadlineAlertClaims {

    private final JdbcTemplate jdbc;

    JdbcDeadlineAlertClaims(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(String tenantId, UUID dossierId, DeadlineLevel level, LocalDate deadline, Instant at) {
        return jdbc.update("""
                insert into dossier_deadline_alert (tenant_id, dossier_id, level, deadline, claimed_at) values (?, ?, ?, ?, ?)
                on conflict do nothing
                """, tenantId, dossierId, level.name(), Date.valueOf(deadline), Timestamp.from(at)) == 1;
    }
}
