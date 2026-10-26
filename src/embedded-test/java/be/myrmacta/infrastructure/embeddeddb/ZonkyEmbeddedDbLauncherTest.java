package be.myrmacta.infrastructure.embeddeddb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZonkyEmbeddedDbLauncherTest {

    private final ZonkyEmbeddedDbLauncher launcher = new ZonkyEmbeddedDbLauncher();

    private static EmbeddedDbSettings settings(Path baseDir) {
        return new EmbeddedDbSettings(baseDir, 0, "myrmacta", "tenant_dev");
    }

    private static Connection open(EmbeddedDbLauncher.RunningDb db) throws SQLException {
        return DriverManager.getConnection(db.jdbcUrl(), db.username(), db.password());
    }

    @Test
    void dataSurvivesARestart_withTheSameGeneratedPassword(@TempDir Path baseDir) throws Exception {
        EmbeddedDbLauncher.RunningDb first = launcher.start(settings(baseDir));
        String password = first.password();
        try (Connection connection = open(first);
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO tenant_dev");
            statement.execute("CREATE TABLE persistence_probe (id int PRIMARY KEY, label text)");
            statement.execute("INSERT INTO persistence_probe VALUES (1, 'avant redémarrage')");
        } finally {
            first.handle().close();
        }

        EmbeddedDbLauncher.RunningDb second = launcher.start(settings(baseDir));
        try (Connection connection = open(second);
                Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO tenant_dev");
            try (ResultSet rs = statement.executeQuery("SELECT label FROM persistence_probe WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("avant redémarrage");
            }
        } finally {
            second.handle().close();
        }
        assertThat(second.password()).as("secret persistant, jamais le mot de passe de démo").isEqualTo(password)
                .isNotEqualTo("myrmacta").hasSize(32);
    }

    @Test
    void appRole_isNotSuperuser_andCannotCreateInPublic(@TempDir Path baseDir) throws Exception {
        EmbeddedDbLauncher.RunningDb db = launcher.start(settings(baseDir));
        try (Connection connection = open(db);
                Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT rolsuper FROM pg_roles WHERE rolname = current_user")) {
                rs.next();
                assertThat(rs.getBoolean(1)).isFalse();
            }
            assertThatThrownBy(() -> statement.execute("CREATE TABLE public.forbidden (id int)"))
                    .isInstanceOf(SQLException.class)
                    // SQLState 42501 (insufficient_privilege) plutôt que le texte, traduit selon la
                    // locale du serveur (« droit refusé » sous un Windows français).
                    .extracting(e -> ((SQLException) e).getSQLState())
                    .isEqualTo("42501");
            statement.execute("CREATE TABLE tenant_dev.allowed (id int)");
        } finally {
            db.handle().close();
        }
    }

    @Test
    void embeddedServerMajor_matchesTheExpectedMajor(@TempDir Path baseDir) throws Exception {
        EmbeddedDbLauncher.RunningDb db = launcher.start(settings(baseDir));
        try (Connection connection = open(db);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SHOW server_version_num")) {
            rs.next();
            assertThat(rs.getInt(1) / 10_000).isEqualTo(ZonkyEmbeddedDbLauncher.PG_MAJOR);
        } finally {
            db.handle().close();
        }
    }

    @Test
    void secondInstanceOnTheSameDirectory_isRefused(@TempDir Path baseDir) throws Exception {
        EmbeddedDbLauncher.RunningDb running = launcher.start(settings(baseDir));
        try {
            assertThatThrownBy(() -> launcher.start(settings(baseDir)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Une autre instance");
        } finally {
            running.handle().close();
        }
    }

    @Test
    void clusterFromAnotherMajorVersion_isRefusedWithAClearMessage(@TempDir Path baseDir) throws Exception {
        Path pgData = Files.createDirectories(baseDir.resolve("pgdata"));
        Files.writeString(pgData.resolve("PG_VERSION"), "16\n");

        assertThatThrownBy(() -> launcher.start(settings(baseDir)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PostgreSQL 16")
                .hasMessageContaining("PostgreSQL " + ZonkyEmbeddedDbLauncher.PG_MAJOR)
                .hasMessageContaining("embedded-db.sh reset");
    }
}
