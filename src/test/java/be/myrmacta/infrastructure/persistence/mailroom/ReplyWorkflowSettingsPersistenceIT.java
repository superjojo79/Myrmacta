package be.myrmacta.infrastructure.persistence.mailroom;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting;
import be.myrmacta.domain.mailroom.model.ReplyWorkflowSetting.Mode;
import be.myrmacta.domain.mailroom.port.out.ReplyWorkflowSettings;
import be.myrmacta.support.OidcClientTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** TODO §113 (D-MAILROOM-REPLY-WORKFLOW) — workflow de réponse par type : lecture, remplacement, suppression, isolation du tenant. */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class ReplyWorkflowSettingsPersistenceIT {

    private static final String TENANT = "tenant_dev";
    private static final String OTHER = "tenant_autre";

    @Autowired
    private ReplyWorkflowSettings settings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clean() {
        jdbc.update("delete from dossier_type_reply_workflow where tenant_id in (?, ?)", TENANT, OTHER);
    }

    @Test
    void aChildDossierSettingIsReadBackWithItsChildType() {
        settings.save(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.DOSSIER_ENFANT, "traiterReponse", "REPONSE"));

        assertThat(settings.find(TENANT, "FACTURE").orElseThrow())
                .isEqualTo(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.DOSSIER_ENFANT, "traiterReponse", "REPONSE"));
    }

    @Test
    void anOnDossierSettingHasNoChildType() {
        settings.save(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.SUR_DOSSIER, "traiterReponse", null));

        ReplyWorkflowSetting found = settings.find(TENANT, "FACTURE").orElseThrow();

        assertThat(found.mode()).isEqualTo(Mode.SUR_DOSSIER);
        assertThat(found.childTypeCode()).isNull();
    }

    @Test
    void savingAgainReplacesTheSettingOfThatTypeOnly() {
        settings.save(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.SUR_DOSSIER, "a", null));
        settings.save(new ReplyWorkflowSetting(TENANT, "COURRIER", Mode.SUR_DOSSIER, "b", null));

        settings.save(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.DOSSIER_ENFANT, "c", "REPONSE"));

        assertThat(settings.find(TENANT, "FACTURE").orElseThrow())
                .isEqualTo(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.DOSSIER_ENFANT, "c", "REPONSE"));
        assertThat(settings.find(TENANT, "COURRIER").orElseThrow().workflowKey()).isEqualTo("b");
        assertThat(jdbc.queryForObject("select count(*) from dossier_type_reply_workflow where tenant_id = ?", Integer.class, TENANT)).isEqualTo(2);
    }

    @Test
    void anUnknownTypeOrAnotherTenantsTypeIsNotFound() {
        settings.save(new ReplyWorkflowSetting(OTHER, "FACTURE", Mode.SUR_DOSSIER, "a", null));

        assertThat(settings.find(TENANT, "FACTURE")).isEmpty();
        assertThat(settings.find(TENANT, "INCONNU")).isEmpty();
    }

    @Test
    void deletingRemovesOnlyThatTenantsTypeAndIgnoresAnUnknownOne() {
        settings.save(new ReplyWorkflowSetting(TENANT, "FACTURE", Mode.SUR_DOSSIER, "a", null));
        settings.save(new ReplyWorkflowSetting(OTHER, "FACTURE", Mode.SUR_DOSSIER, "b", null));

        settings.delete(TENANT, "FACTURE");
        settings.delete(TENANT, "INCONNU");

        assertThat(settings.find(TENANT, "FACTURE")).isEmpty();
        assertThat(settings.find(OTHER, "FACTURE")).isPresent();
    }
}
