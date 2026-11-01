package be.myrmacta.infrastructure.persistence.deadline;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * TODO §115 (D-DEADLINE-ESCALATE) — lit la valeur du champ d'échéance en base ({@code fields ->> champ}), sans charger les
 * dossiers : ni brouillon, ni clôturé, ni archivé, ni déjà escaladé pour cette date (revue de clôture : sinon chaque passage
 * retentait une prise sur tous les dossiers échus). Comparaison en texte : une valeur qui n'est pas une date ne casse rien.
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
                select d.id, d.reference, d.dossier_type_code, d.fields ->> ? as deadline from dossier d
                where d.tenant_id = ? and d.dossier_type_code = ? and d.status in ('OUVERT', 'EN_COURS', 'SUSPENDU')
                  and d.fields ->> ? is not null
                  and not exists (
                      select 1 from dossier_deadline_alert a
                      where a.tenant_id = d.tenant_id and a.dossier_id = d.id and a.level = 'ESCALADE'
                        and a.deadline::text = left(d.fields ->> ?, 10))
                """, (rs, i) -> new DeadlineCandidate(rs.getObject("id", java.util.UUID.class), rs.getString("reference"),
                        rs.getString("dossier_type_code"), rs.getString("deadline")),
                setting.fieldName(), setting.tenantId(), setting.dossierTypeCode(), setting.fieldName(), setting.fieldName());
    }
}
