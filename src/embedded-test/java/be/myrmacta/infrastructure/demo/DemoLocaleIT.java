package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §127 (D-DEMO-LOCALE, lot 2 de #55) — la démo démarrée en anglais : types, statuts, questionnaires, tâches et
 * données semées sont en anglais, les codes restent les mêmes.
 */
class DemoLocaleIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db,demo",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.fake-apis.port=0",
                "--app.demo.locale=en",
                // Même coupure que MailRoomDemoSeederIT : les paquets font eux-mêmes avancer les jobs de leurs processus.
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theDemoInEnglish(@TempDir Path baseDir) {
        try (ConfigurableApplicationContext context = run(baseDir)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select label from dossier_type where code = 'COURRIER'", String.class))
                    .isEqualTo("Incoming mail");
            assertThat(jdbc.queryForObject("select label from dossier_type where code = 'ONBOARDING_COLLABORATEUR'",
                    String.class)).isEqualTo("New hire");
            assertThat(jdbc.queryForObject("select label from task_question_schema "
                    + "where schema_key = 'validation-manager-onboarding'", String.class)).isEqualTo("Manager approval");
            assertThat(jdbc.queryForObject("select count(*) from dossier where dossier_type_code = 'FACTURE' "
                    + "and label like 'Invoice %'", Integer.class)).as("factures de l'historique").isPositive();

            TaskService tasks = context.getBean(TaskService.class);
            assertThat(tasks.createTaskQuery().taskDefinitionKey("validationManager").list())
                    .extracting(task -> task.getName()).containsOnly("Approve the new hire");
        }
    }
}
