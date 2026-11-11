package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import org.flowable.engine.ManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §127 (D-DEMO-BACKDATE, lot 1d de #55) — le paquet « états » de la démo : des objectifs SLA sur les types, un
 * historique de factures antidatées (traitées dans le délai, en retard, ouvertes et dépassées), un incident rejouable sur
 * une arrivée (le faux SIRH coupe la connexion) et un brouillon de la table de routage proposé par MCP.
 */
class StatesDemoPackIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db,demo",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.fake-apis.port=0",
                // Même coupure que MailRoomDemoSeederIT : les paquets font eux-mêmes avancer les jobs de leurs processus.
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theStatesPackGivesTheScreensSomethingToShow(@TempDir Path baseDir) {
        try (ConfigurableApplicationContext context = run(baseDir)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            assertThat(count(jdbc, "select count(*) from dossier_type where code = 'FACTURE' and sla_target_hours = 72"))
                    .as("objectif SLA de la facture").isEqualTo(1);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE' "
                    + "and created_at < now() - interval '20 days'"))
                    .as("un historique de factures sur plusieurs semaines").isPositive();
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE' "
                    + "and closed_at is not null and closed_at - created_at <= interval '72 hours'"))
                    .as("factures traitées dans le délai").isGreaterThanOrEqualTo(3);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE' "
                    + "and closed_at is not null and closed_at - created_at > interval '72 hours'"))
                    .as("factures traitées en retard").isGreaterThanOrEqualTo(1);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE' "
                    + "and closed_at is null and created_at < now() - interval '72 hours'"))
                    .as("factures ouvertes au SLA dépassé").isGreaterThanOrEqualTo(2);

            assertThat(context.getBean(ManagementService.class).createDeadLetterJobQuery().count())
                    .as("un incident rejouable").isEqualTo(1);
            assertThat(context.getBean(DemoFakeApis.class).received()).contains("POST /sirh/employees");

            assertThat(count(jdbc, "select count(*) from dmn_draft where decision_key = 'routageCourrier'"))
                    .as("un brouillon MCP de la table de routage").isEqualTo(1);
            assertThat(jdbc.queryForObject("select xml from dmn_draft where decision_key = 'routageCourrier'", String.class))
                    .as("en DMN 1.3, pour que l'éditeur l'ouvre ; avec la règle « imprimerie »")
                    .contains(StatesDemoPack.DMN_13).doesNotContain(StatesDemoPack.DMN_11).contains("regleFactureImprimerie");
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
