package be.myrmacta.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;

import be.myrmacta.infrastructure.observability.LiquibaseMigrationReader;
import be.myrmacta.infrastructure.observability.LiquibaseMigrationStatus;
import be.myrmacta.support.EmbeddedPostgresSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Vérifie que {@link LiquibaseMigrationReader} lit correctement l'état de migration Liquibase
 * depuis {@code DATABASECHANGELOG} sur un vrai schéma PostgreSQL — §119.
 */
class LiquibaseMigrationReaderIT {

    private static final String SCHEMA = "tenant_reader_it";

    private HikariDataSource dataSource;
    private LiquibaseMigrationReader reader;

    @BeforeEach
    void setUp() {
        EmbeddedPostgresSupport.createSchema(SCHEMA);
        EmbeddedPostgresSupport.migrateSchema(SCHEMA);
        dataSource = EmbeddedPostgresSupport.dataSourceForSchema(SCHEMA);
        reader = new LiquibaseMigrationReader(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
        EmbeddedPostgresSupport.dropSchema(SCHEMA);
    }

    @Test
    void appliedCount_matchesNumberOfChangesetsInMasterChangelog() {
        LiquibaseMigrationStatus status = reader.read();

        assertThat(status.appliedChangesetsCount())
                .as("des changesets ont été appliqués par la migration")
                .isGreaterThan(0);
    }

    @Test
    void lastAppliedChangesetId_isNonEmpty() {
        LiquibaseMigrationStatus status = reader.read();

        assertThat(status.lastAppliedChangesetId())
                .as("le dernier changeset doit avoir un identifiant")
                .isNotBlank();
    }

    @Test
    void lastAppliedAt_isARecentTimestamp() {
        LiquibaseMigrationStatus status = reader.read();

        assertThat(status.lastAppliedAt())
                .as("le timestamp du dernier changeset doit être dans le passé")
                .isNotNull()
                .isBefore(Instant.now().plusSeconds(5));
    }

    @Test
    void emptySchema_returnsZeroAndNulls() throws Exception {
        // Schéma sans migration Liquibase : DATABASECHANGELOG n'existe pas.
        String emptySchema = "tenant_reader_empty_it";
        EmbeddedPostgresSupport.createSchema(emptySchema);
        HikariDataSource emptyDs = EmbeddedPostgresSupport.dataSourceForSchema(emptySchema);
        try {
            LiquibaseMigrationReader emptyReader = new LiquibaseMigrationReader(emptyDs);
            LiquibaseMigrationStatus status = emptyReader.read();

            assertThat(status.appliedChangesetsCount()).isZero();
            assertThat(status.lastAppliedChangesetId()).isNull();
            assertThat(status.lastAppliedAt()).isNull();
        } finally {
            emptyDs.close();
            EmbeddedPostgresSupport.dropSchema(emptySchema);
        }
    }
}
