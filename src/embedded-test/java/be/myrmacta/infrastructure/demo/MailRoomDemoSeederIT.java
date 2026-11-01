package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.flowable.engine.ManagementService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §113 (lot 7 de #217, critère 9) — la démo reproductible : sur une base embarquée vide, le profil {@code demo-mailroom}
 * installe l'exemple et fait arriver trois courriers ; la facture est routée vers la comptabilité (dossier « facture » lié),
 * l'amende déposée et l'e-mail inconnu vont à la file de tri. Relancée sur la même base, la démo ne réinstalle rien.
 */
class MailRoomDemoSeederIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db,demo-mailroom",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.mailroom.dir=exemples/digital-mail-room",
                "--server.port=0");
    }

    @Test
    void theDemoInstallsTheExample_andThreeMailItemsArrive_onlyOnce(@TempDir Path baseDir) throws Exception {
        try (ConfigurableApplicationContext first = run(baseDir)) {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'COURRIER'")).isEqualTo(3);
            awaitTriage(first);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE'")).isEqualTo(1);
            TaskService tasks = first.getBean(TaskService.class);
            assertThat(tasks.createTaskQuery().taskDefinitionKey("trierCourrier").count()).isEqualTo(2);
            assertThat(tasks.createTaskQuery().taskDefinitionKey("traiterFactureTache").count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from dossier where dossier_type_code = 'COURRIER' "
                    + "and fields ->> 'canal' = 'PAPIER'", Integer.class)).isEqualTo(1);
        }
        try (ConfigurableApplicationContext second = run(baseDir)) {
            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'COURRIER'"))
                    .as("la démo ne réinstalle rien sur une base déjà installée").isEqualTo(3);
        }
    }

    /** Exécuteur asynchrone coupé en test : les jobs du tri sont exécutés ici, jusqu'à ce que les 3 courriers soient triés. */
    private static void awaitTriage(ConfigurableApplicationContext context) throws InterruptedException {
        ManagementService management = context.getBean(ManagementService.class);
        TaskService tasks = context.getBean(TaskService.class);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)) {
            Integer pending = jdbc.queryForObject("select count(*) from outbox_event where status = 'PENDING'", Integer.class);
            if (pending != null && pending == 0) {
                for (var job : management.createJobQuery().list()) {
                    management.executeJob(job.getId());
                }
                if (tasks.createTaskQuery().taskDefinitionKey("trierCourrier").count() == 2
                        && tasks.createTaskQuery().taskDefinitionKey("traiterFactureTache").count() == 1) {
                    return;
                }
            }
            Thread.sleep(250);
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
