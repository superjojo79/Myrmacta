package be.myrmacta.infrastructure.embeddeddb;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.MyrmactaApplication;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Bout en bout du profil {@code embedded-db} : l'application complète démarre sur la base
 * embarquée, une donnée est écrite, l'application est arrêtée puis relancée sur le même
 * répertoire — la donnée (et le schéma Liquibase) sont toujours là, sans re-migration.
 */
class EmbeddedDbProfileRestartIT {

    private static ConfigurableApplicationContext run(Path baseDir) {
        SpringApplication application = new SpringApplication(MyrmactaApplication.class, OidcClientTestConfiguration.class);
        return application.run(
                "--spring.profiles.active=test,embedded-db",
                "--test.embedded-postgres.enabled=false",
                "--app.embedded-db.data-dir=" + baseDir,
                "--app.embedded-db.port=0",
                "--server.port=0");
    }

    @Test
    void applicationData_survivesAnApplicationRestart(@TempDir Path baseDir) {
        int changesets;
        try (ConfigurableApplicationContext first = run(baseDir)) {
            JdbcTemplate jdbc = first.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo("tenant_dev");
            changesets = jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class);
            assertThat(changesets).isPositive();
            jdbc.update("CREATE TABLE restart_probe (id int PRIMARY KEY, label text)");
            jdbc.update("INSERT INTO restart_probe VALUES (1, 'avant redémarrage')");
        }

        try (ConfigurableApplicationContext second = run(baseDir)) {
            JdbcTemplate jdbc = second.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("SELECT label FROM restart_probe WHERE id = 1", String.class))
                    .isEqualTo("avant redémarrage");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM databasechangelog", Integer.class))
                    .as("aucune re-migration Liquibase au redémarrage")
                    .isEqualTo(changesets);
        }
        assertThat(Files.exists(baseDir.resolve("app-role-password"))).isTrue();
    }
}
