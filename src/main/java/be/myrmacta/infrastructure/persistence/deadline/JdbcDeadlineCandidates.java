package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TODO §115 (D-DEADLINE-ESCALATE) — lit la valeur du champ d'échéance en base ({@code fields ->> champ}), sans charger les
 * dossiers : ni brouillon, ni clôturé, ni archivé.
 */
@Component
class JdbcDeadlineCandidates implements DeadlineCandidates {

    private final JdbcTemplate jdbc;

    JdbcDeadlineCandidates(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DeadlineCandidate> list(DeadlineSetting setting) {
        return jdbc.query("""
                select id, reference, dossier_type_code, fields ->> ? as deadline from dossier
                where tenant_id = ? and dossier_type_code = ? and status in ('OUVERT', 'EN_COURS', 'SUSPENDU')
                  and fields ->> ? is not null
                """, (rs, i) -> new DeadlineCandidate(rs.getObject("id", java.util.UUID.class), rs.getString("reference"),
                        rs.getString("dossier_type_code"), rs.getString("deadline")),
                setting.fieldName(), setting.tenantId(), setting.dossierTypeCode(), setting.fieldName());
    }
}
