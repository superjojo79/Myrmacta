package be.myrmacta.infrastructure.persistence.deadline;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.deadline.model.DayCount;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §115 (D-DEADLINE-FIELD) — réglage d'échéance par type : lecture, remplacement, suppression, isolation du tenant. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class DeadlineSettingsPersistenceIT {

    private static final String TENANT = "tenant_dev";
    private static final String OTHER = "tenant_autre";

    @Autowired
    private DeadlineSettings settings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("delete from dossier_type_deadline where tenant_id in (?, ?)", TENANT, OTHER);
    }

    @Test
    void aSavedSettingIsReadBackWithAllItsFields() {
        settings.save(new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, "DIRECTION", DayCount.BUSINESS));

        DeadlineSetting found = settings.find(TENANT, "FACTURE").orElseThrow();

        assertThat(found).isEqualTo(new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, "DIRECTION", DayCount.BUSINESS));
    }

    @Test
    void anAbsentEscalationGroupAndTheCalendarModeRoundTrip() {
        settings.save(new DeadlineSetting(TENANT, "FACTURE", "echeance", 0, null));

        DeadlineSetting found = settings.find(TENANT, "FACTURE").orElseThrow();

        assertThat(found.escalationGroup()).isNull();
        assertThat(found.reminderDays()).isZero();
        assertThat(found.reminderDayCount()).isEqualTo(DayCount.CALENDAR);
    }

    @Test
    void savingAgainReplacesTheSettingOfThatTypeOnly() {
        settings.save(new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, "DIRECTION"));
        settings.save(new DeadlineSetting(TENANT, "COURRIER", "reponse", 2, null));

        settings.save(new DeadlineSetting(TENANT, "FACTURE", "paiement", 10, null, DayCount.BUSINESS));

        assertThat(settings.find(TENANT, "FACTURE").orElseThrow())
                .isEqualTo(new DeadlineSetting(TENANT, "FACTURE", "paiement", 10, null, DayCount.BUSINESS));
        assertThat(settings.find(TENANT, "COURRIER").orElseThrow().fieldName()).isEqualTo("reponse");
        assertThat(jdbc.queryForObject("select count(*) from dossier_type_deadline where tenant_id = ?", Integer.class, TENANT)).isEqualTo(2);
    }

    @Test
    void findAllIsOrderedByTypeCodeAndScopedToTheTenant() {
        settings.save(new DeadlineSetting(TENANT, "ZEBRE", "a", 1, null));
        settings.save(new DeadlineSetting(TENANT, "ALPHA", "b", 2, null));
        settings.save(new DeadlineSetting(OTHER, "MILIEU", "c", 3, null));

        assertThat(settings.findAll(TENANT)).extracting(DeadlineSetting::dossierTypeCode).containsExactly("ALPHA", "ZEBRE");
        assertThat(settings.findAll(OTHER)).extracting(DeadlineSetting::dossierTypeCode).containsExactly("MILIEU");
        assertThat(settings.findAll("tenant_vide")).isEmpty();
    }

    @Test
    void anUnknownTypeOrAnotherTenantsTypeIsNotFound() {
        settings.save(new DeadlineSetting(OTHER, "FACTURE", "echeance", 5, null));

        assertThat(settings.find(TENANT, "FACTURE")).isEmpty();
        assertThat(settings.find(TENANT, "INCONNU")).isEmpty();
    }

    @Test
    void deletingRemovesOnlyThatTenantsTypeAndIgnoresAnUnknownOne() {
        settings.save(new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, null));
        settings.save(new DeadlineSetting(TENANT, "COURRIER", "reponse", 2, null));
        settings.save(new DeadlineSetting(OTHER, "FACTURE", "echeance", 7, null));

        settings.delete(TENANT, "FACTURE");
        settings.delete(TENANT, "INCONNU");

        assertThat(settings.find(TENANT, "FACTURE")).isEmpty();
        assertThat(settings.find(TENANT, "COURRIER")).isPresent();
        assertThat(settings.find(OTHER, "FACTURE")).isPresent();
    }

    @Test
    void aRowWrittenBeforeTheCalendarModeExistedIsCalendar() {
        jdbc.update("insert into dossier_type_deadline (tenant_id, dossier_type_code, field_name, reminder_days) values (?, 'ANCIEN', 'echeance', 3)",
                TENANT);

        assertThat(settings.find(TENANT, "ANCIEN").orElseThrow().reminderDayCount()).isEqualTo(DayCount.CALENDAR);
    }
}
