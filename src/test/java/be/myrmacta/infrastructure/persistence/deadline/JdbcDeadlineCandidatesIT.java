package be.myrmacta.infrastructure.persistence.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineLevel;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineAlertClaims;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §115 (D-DEADLINE-ESCALATE, revue de clôture de #217) — un dossier déjà escaladé pour sa date n'est plus candidat ; reporter
 * l'échéance le rend de nouveau candidat ; une valeur qui n'est pas une date ne casse pas la requête.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JdbcDeadlineCandidatesIT {

    private static final String TENANT = "tenant_dev";

    @Autowired private DeadlineCandidates candidates;
    @Autowired private DeadlineAlertClaims claims;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anEscalatedDossierIsNoLongerACandidate_untilItsDeadlineMoves() {
        String type = "ECH_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        DeadlineSetting setting = new DeadlineSetting(TENANT, type, "echeance", 0, null);
        UUID escalated = dossier(type, "2026-11-30");
        UUID open = dossier(type, "2026-12-15");
        dossier(type, "bientôt");
        claims.claim(TENANT, escalated, DeadlineLevel.ESCALADE, LocalDate.parse("2026-11-30"), Instant.now());

        assertThat(candidates.list(setting)).extracting(DeadlineCandidate::dossierId).contains(open).doesNotContain(escalated);

        jdbc.update("update dossier set fields = '{\"echeance\":\"2026-12-31\"}' where id = ?", escalated);
        assertThat(candidates.list(setting)).extracting(DeadlineCandidate::dossierId).contains(escalated);
    }

    private UUID dossier(String type, String deadline) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into dossier (id, tenant_id, reference, dossier_type_code, label, status, fields, version, created_by, created_at,
                                     updated_by, updated_at)
                values (?, ?, ?, ?, 'x', 'EN_COURS', cast(? as json), 0, 't', now(), 't', now())
                """, id, TENANT, "DOS-" + type + "-" + id.toString().substring(0, 8), type, "{\"echeance\":\"" + deadline + "\"}");
        return id;
    }
}
