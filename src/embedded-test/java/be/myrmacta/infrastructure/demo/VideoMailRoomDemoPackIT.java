package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.domain.connector.model.ConnectorInstanceData;
import be.myrmacta.domain.connector.port.in.ConnectorTriggerRuntime;
import be.myrmacta.domain.connector.port.out.ConnectorInstanceRepository;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.deadline.DeadlineAlertJob;
import be.myrmacta.support.OidcClientTestConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.user.GreenMailUser;
import com.icegreen.greenmail.util.ServerSetupTest;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.flowable.engine.ManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §128 (D-VIDEO-MAIL, D-VIDEO-DEADLINE, lot 2 de #56) — la scène de la capsule « Digital Mail Room » : la mail room sans
 * ses courriers d'exemple, une boîte IMAP sur le GreenMail local que le vrai connecteur relève, l'agent IA dans la file de
 * tri, deux factures dont l'échéance est proche ou passée.
 */
class VideoMailRoomDemoPackIT {

    @RegisterExtension
    static final GreenMailExtension GREEN = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db," + VideoMailRoomDemoPack.PROFILE,
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.video.imap-port=" + ServerSetupTest.IMAP.getPort(),
                "--app.inbound.initial-delay=1h",
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theSceneIsSet_andAnEmailSentToTheDemoMailboxArrivesThroughTheRealConnector(@TempDir Path baseDir) throws Exception {
        GreenMailUser mailbox = GREEN.setUser("courrier@demo.example", "courrier", "demo");
        try (ConfigurableApplicationContext context = run(baseDir)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String tenant = context.getBean(TenantProperties.class).id();

            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'COURRIER'"))
                    .as("pas de courriers d'exemple : seulement les deux factures de l'échéance").isEqualTo(2);
            assertThat(jdbc.queryForList("select fields->>'echeance' from dossier where dossier_type_code = 'FACTURE'", String.class))
                    .containsExactlyInAnyOrder(LocalDate.now().plusDays(3).toString(), LocalDate.now().minusDays(2).toString());
            assertThat(context.getBean(UserGroupsUseCase.class).list()).filteredOn(g -> g.code().equals("COURRIER")).singleElement()
                    .satisfies(g -> assertThat(g.memberIds()).contains("agent-ia-tri"));

            assertThat(jdbc.queryForObject("select xml from dmn_draft where decision_key = 'routageCourrier'", String.class))
                    .as("le brouillon de la conclusion, en DMN 1.3").contains(StatesDemoPack.DMN_13).contains("regleFactureImprimerie");

            List<ConnectorInstanceData> imap = context.getBean(ConnectorInstanceRepository.class).listByTenantId(tenant).stream()
                    .filter(i -> i.connectorType().equals("imap-inbound")).toList();
            assertThat(context.getBean(ConnectorInstanceRepository.class).listByTenantId(tenant))
                    .as("le point de dépôt de l'écran « Déposer un courrier » (chapitre de l'amende)")
                    .anySatisfy(i -> assertThat(i.connectorType()).isEqualTo("mail-deposit"));
            assertThat(imap).singleElement().satisfies(i -> {
                assertThat(i.enabled()).isTrue();
                assertThat(i.config()).containsEntry("host", "localhost").containsEntry("security", "PLAIN");
            });

            // D-VIDEO-DEADLINE : le vrai job des échéances rappelle la facture proche et escalade celle qui est échue.
            context.getBean(DeadlineAlertJob.class).process(Instant.now());
            assertThat(jdbc.queryForList("select level from dossier_deadline_alert", String.class))
                    .as("un rappel (échéance dans 3 jours) et une escalade (échue avant-hier)").hasSize(2).doesNotHaveDuplicates();
            assertThat(count(jdbc, "select count(*) from act_ru_identitylink where type_ = 'candidate' "
                    + "and group_id_ = 'DIRECTION_FINANCIERE'")).as("tâche de la facture échue réaffectée à l'escalade").isPositive();

            mailbox.deliver(invoice());
            context.getBean(ConnectorTriggerRuntime.class).onIncomingEvent(imap.getFirst(), Map.of());

            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'COURRIER' "
                    + "and fields->>'objet' = 'Facture 2026-117'")).as("l'e-mail est arrivé par IMAP").isEqualTo(1);
            awaitInvoices(context, 3);
            assertThat(count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE'"))
                    .as("routé vers la comptabilité, dans un dossier « facture »").isEqualTo(3);
        }
    }

    /** Le routage est asynchrone et l'exécuteur coupé : jobs exécutés une fois l'outbox vidée (même motif qu'awaitTriage). */
    private static void awaitInvoices(ConfigurableApplicationContext context, int expected) throws InterruptedException {
        ManagementService management = context.getBean(ManagementService.class);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(60));
        while (Instant.now().isBefore(deadline)
                && count(jdbc, "select count(*) from dossier where dossier_type_code = 'FACTURE'") < expected) {
            if (count(jdbc, "select count(*) from outbox_event where status = 'PENDING'") == 0) {
                for (var job : management.createJobQuery().list()) {
                    management.executeJob(job.getId());
                }
            }
            Thread.sleep(250);
        }
    }

    private static MimeMessage invoice() throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("compta@fournisseur.example"));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress("courrier@demo.example"));
        message.setSubject("Facture 2026-117");
        message.setText("Veuillez trouver notre facture 2026-117.");
        message.setHeader("Authentication-Results", VideoMailRoomDemoPack.TRUSTED_AUTHSERV_ID + "; dkim=pass header.d=fournisseur.example");
        return message;
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
