package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §127 (D-DEMO-FAKE-APIS, lot 1b de #55) — le paquet onboarding de la démo : l'exemple
 * {@code exemples/onboarding-collaborateur} est installé, ses connecteurs pointent sur les faux SIRH, IAM et ITSM embarqués,
 * et trois arrivées sont semées par le vrai processus : une provisionnée (champs « Provisioning » remplis), une qui attend
 * la validation du manager, une qui attend la validation des accès par l'IT.
 */
class OnboardingDemoPackIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db,demo",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.fake-apis.port=0",
                // Même coupure que MailRoomDemoSeederIT : le paquet fait lui-même avancer les jobs de ses processus.
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theOnboardingPackSeedsThreeArrivals_throughTheRealProcess(@TempDir Path baseDir) {
        try (ConfigurableApplicationContext context = run(baseDir)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'ONBOARDING_COLLABORATEUR'"))
                    .isEqualTo(3);

            List<Map<String, Object>> provisioned = jdbc.queryForList("select fields ->> 'matriculeSirh' as sirh, "
                    + "fields ->> 'identifiantIam' as iam, fields ->> 'ticketMateriel' as ticket from dossier "
                    + "where dossier_type_code = 'ONBOARDING_COLLABORATEUR' and fields ->> 'ticketMateriel' is not null");
            assertThat(provisioned).as("une arrivée provisionnée par le vrai processus").hasSize(1);
            assertThat(provisioned.getFirst().get("sirh")).isNotNull();
            assertThat(provisioned.getFirst().get("iam")).isNotNull();

            TaskService tasks = context.getBean(TaskService.class);
            assertThat(tasks.createTaskQuery().taskDefinitionKey("validationManager").count()).isEqualTo(1);
            assertThat(tasks.createTaskQuery().taskDefinitionKey("validationAcces").count()).isEqualTo(1);

            DemoFakeApis apis = context.getBean(DemoFakeApis.class);
            assertThat(apis.received()).contains("POST /sirh/employees", "POST /iam/users", "POST /itsm/tickets");
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
