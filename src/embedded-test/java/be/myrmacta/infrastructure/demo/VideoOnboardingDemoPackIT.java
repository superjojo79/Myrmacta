package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §136 (D-DEMO-FAKE-FAULTS, D-VIDEO-OFFSCREEN-PREP, issue #57) — la scène de la capsule L2 : l'exemple onboarding
 * installé sans arrivée, et les deux scènes de la partie 2 rejouées par le vrai processus, pannes commandées sur les faux
 * services : l'IAM en panne puis « Réessayer », l'ITSM en panne puis « Abandonner » (compensation IAM puis SIRH).
 */
class VideoOnboardingDemoPackIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db," + OnboardingDemoPack.VIDEO_PROFILE,
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.fake-apis.port=0",
                // Le paquet fait lui-même avancer les jobs de ses processus (même coupure que OnboardingDemoPackIT).
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theSceneHasTheExampleWithoutArrivals_andReplaysRetryThenAbandon(@TempDir Path baseDir) {
        try (ConfigurableApplicationContext context = run(baseDir)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            OnboardingDemoPack pack = context.getBean(OnboardingDemoPack.class);
            DemoFakeApis apis = context.getBean(DemoFakeApis.class);
            TaskService tasks = context.getBean(TaskService.class);
            RuntimeService runtime = context.getBean(RuntimeService.class);

            assertThat(pack.installed()).isTrue();
            assertThat(onboardingDossiers(jdbc)).as("aucune arrivée semée : le tournage les encode").isZero();
            assertThat(apis.received()).isEmpty();

            // Partie 2, scène 6 : l'IAM répond en erreur, une personne réessaie, l'appel repasse.
            apis.arm("iam", 503, 1);
            String retried = pack.arrive("Lucas", "Martin", "lucas.martin@example.org", "Développeur", "IT", "DEVELOPPEUR");
            pack.complete(retried, "validationManager", Map.of("decisionManager", "APPROUVER"));
            pack.advanceTo(retried, "traiterIncident");
            assertThat(field(jdbc, "lucas.martin@example.org", "matriculeSirh")).isNotNull();
            assertThat(field(jdbc, "lucas.martin@example.org", "identifiantIam")).isNull();
            assertThat(field(jdbc, "lucas.martin@example.org", "dernierIncident")).contains("503");
            pack.complete(retried, "traiterIncident", Map.of("actionIncident", "REESSAYER"));
            pack.advanceTo(retried, "validationAcces");
            assertThat(field(jdbc, "lucas.martin@example.org", "identifiantIam")).isNotNull();

            // Partie 2, scène 7 : l'ITSM échoue, une personne abandonne ; le compte IAM puis la fiche SIRH sont supprimés.
            apis.arm("itsm", 500, 1);
            String abandoned = pack.arrive("Inès", "Lambert", "ines.lambert@example.org", "Analyste", "FINANCE", "STANDARD");
            pack.complete(abandoned, "validationManager", Map.of("decisionManager", "APPROUVER"));
            pack.advanceTo(abandoned, "traiterIncident");
            String iamId = field(jdbc, "ines.lambert@example.org", "identifiantIam");
            String sirhId = field(jdbc, "ines.lambert@example.org", "matriculeSirh");
            assertThat(iamId).isNotNull();
            assertThat(sirhId).isNotNull();
            pack.complete(abandoned, "traiterIncident", Map.of("actionIncident", "ABANDONNER"));
            pack.advanceToEnd(abandoned);

            assertThat(apis.received()).containsSubsequence("POST /itsm/tickets", "DELETE /iam/users/" + iamId,
                    "DELETE /sirh/employees/" + sirhId);
            assertThat(field(jdbc, "ines.lambert@example.org", "ticketMateriel")).isNull();
            assertThat(runtime.createProcessInstanceQuery().processInstanceId(abandoned).count()).isZero();
            assertThat(tasks.createTaskQuery().processInstanceId(retried).taskDefinitionKey("validationAcces").count())
                    .isEqualTo(1);
        }
    }

    private static int onboardingDossiers(JdbcTemplate jdbc) {
        Integer value = jdbc.queryForObject(
                "select count(*) from dossier where dossier_type_code = 'ONBOARDING_COLLABORATEUR'", Integer.class);
        return value == null ? 0 : value;
    }

    private static String field(JdbcTemplate jdbc, String email, String name) {
        return jdbc.queryForObject("select fields ->> ? from dossier where dossier_type_code = 'ONBOARDING_COLLABORATEUR' "
                + "and fields ->> 'emailPersonnel' = ?", String.class, name, email);
    }
}
