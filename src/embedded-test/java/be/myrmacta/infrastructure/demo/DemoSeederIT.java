package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §127 (D-DEMO-PACKS, issue #55) — le profil {@code demo} installe ses paquets sur une base embarquée vide, et ne
 * réinstalle rien au démarrage suivant. Le contenu de chaque paquet est vérifié par son propre test
 * ({@link MailRoomDemoSeederIT} pour la mail room).
 */
class DemoSeederIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db,demo",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--app.demo.fake-apis.port=0",
                // Même coupure que MailRoomDemoSeederIT (précédent « course avec le relais de l'outbox »).
                "--flowable.async-executor-activate=false",
                "--server.port=0");
    }

    @Test
    void theDemoProfileInstallsItsPacks_onlyOnce(@TempDir Path baseDir) {
        try (ConfigurableApplicationContext first = run(baseDir)) {
            assertThat(first.getBeansOfType(DemoPack.class)).isNotEmpty();
            assertThat(courriers(first)).as("paquet mail room installé").isEqualTo(3);
        }
        try (ConfigurableApplicationContext second = run(baseDir)) {
            assertThat(courriers(second)).as("rien n'est réinstallé sur une base déjà installée").isEqualTo(3);
        }
    }

    private static int courriers(ConfigurableApplicationContext context) {
        Integer value = context.getBean(JdbcTemplate.class)
                .queryForObject("select count(*) from dossier where dossier_type_code = 'COURRIER'", Integer.class);
        return value == null ? 0 : value;
    }
}
