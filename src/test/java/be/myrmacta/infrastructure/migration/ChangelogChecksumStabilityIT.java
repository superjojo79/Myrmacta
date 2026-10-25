package be.myrmacta.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.support.EmbeddedPostgresSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou « on ne modifie jamais un changeSet déjà appliqué » : migre le changelog réel sur un
 * PostgreSQL vierge et compare les {@code MD5SUM} de {@code DATABASECHANGELOG} à la référence
 * versionnée {@code db/changelog/checksums-baseline.csv}. Toute modification d'un changeSet
 * existant (donc d'une base de production déjà migrée) change son checksum et fait échouer ce
 * test ; les changeSets ajoutés après la référence sont autorisés.
 *
 * <p>Prouve aussi la reprise idempotente : un second {@code update} sur la base déjà migrée
 * n'applique rien et ne lève aucune erreur de checksum.
 *
 * <p>Régénérer la référence (uniquement après revue, jamais pour « réparer » un échec) :
 * {@code ./mvnw test -Dtest=ChangelogChecksumStabilityIT -Dchecksum.baseline.write=true}.
 */
class ChangelogChecksumStabilityIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final String BASELINE = "db/changelog/checksums-baseline.csv";
    private static final Path BASELINE_SOURCE = Path.of("src/test/resources/" + BASELINE);

    @Test
    void appliedChangesetChecksums_matchTheCommittedBaseline_andRerunIsIdempotent() throws Exception {
        EmbeddedPostgresSupport.ApplicationDatabase database = EmbeddedPostgresSupport.newApplicationDatabase();
        String url = database.jdbcUrl() + "?currentSchema=tenant_dev";

        migrate(url, database);
        Map<String, String> actual = readChecksums(url, database);
        int rowsAfterFirstRun = actual.size();

        migrate(url, database); // reprise : aucune erreur de checksum, rien de ré-appliqué
        assertThat(readChecksums(url, database)).hasSize(rowsAfterFirstRun);

        if (Boolean.getBoolean("checksum.baseline.write")) {
            writeBaseline(actual);
        }

        Map<String, String> baseline = loadBaseline();
        assertThat(baseline).as("référence %s vide ou absente", BASELINE).isNotEmpty();
        List<String> drifts = new ArrayList<>();
        baseline.forEach((key, expected) -> {
            String found = actual.get(key);
            if (found == null) {
                drifts.add(key + " : changeSet supprimé ou renommé");
            } else if (!found.equals(expected)) {
                drifts.add(key + " : checksum " + expected + " -> " + found);
            }
        });
        assertThat(drifts)
                .as("un changeSet déjà appliqué en production a été modifié (créer un nouveau changeSet à la place)")
                .isEmpty();
    }

    private static void migrate(String url, EmbeddedPostgresSupport.ApplicationDatabase database) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, database.username(), database.password())) {
            Database liquibaseDatabase = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), liquibaseDatabase).update();
        }
    }

    /** Clé {@code fichier::id::auteur} → {@code md5sum}, dans l'ordre d'exécution. */
    private static Map<String, String> readChecksums(String url, EmbeddedPostgresSupport.ApplicationDatabase database)
            throws SQLException {
        Map<String, String> checksums = new LinkedHashMap<>();
        try (Connection connection = DriverManager.getConnection(url, database.username(), database.password());
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT filename, id, author, md5sum FROM databasechangelog ORDER BY orderexecuted")) {
            while (rs.next()) {
                checksums.put(rs.getString(1) + "::" + rs.getString(2) + "::" + rs.getString(3), rs.getString(4));
            }
        }
        return checksums;
    }

    private static Map<String, String> loadBaseline() throws IOException {
        Map<String, String> baseline = new LinkedHashMap<>();
        try (InputStream in = ChangelogChecksumStabilityIT.class.getClassLoader().getResourceAsStream(BASELINE)) {
            if (in == null) {
                return baseline;
            }
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int cut = line.lastIndexOf(',');
                baseline.put(line.substring(0, cut), line.substring(cut + 1).trim());
            }
        }
        return baseline;
    }

    private static void writeBaseline(Map<String, String> checksums) throws IOException {
        StringBuilder out = new StringBuilder("# Référence des checksums Liquibase (voir ChangelogChecksumStabilityIT)\n");
        checksums.forEach((key, md5) -> out.append(key).append(',').append(md5).append('\n'));
        Files.writeString(BASELINE_SOURCE, out.toString(), StandardCharsets.UTF_8);
    }
}
