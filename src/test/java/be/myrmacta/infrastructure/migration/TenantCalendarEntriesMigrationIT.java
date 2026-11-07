package be.myrmacta.infrastructure.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.support.EmbeddedPostgresSupport;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §121 (D-CAL-BE-SOURCE) — le changeset 047 reprend les dates de {@code tenant_calendar.holidays} (046) en
 * saisies manuelles {@code CLOSED}, puis retire la colonne ; ses tables tiennent les garde-fous de D-CAL-SOURCE.
 */
class TenantCalendarEntriesMigrationIT {

    private static final String SCHEMA = "tenant_calendar_047_it";
    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;

    @BeforeEach
    void migrateEverythingButTheLastChangesetThenInsertLegacyRows() throws Exception {
        EmbeddedPostgresSupport.createSchema(SCHEMA);
        dataSource = EmbeddedPostgresSupport.dataSourceForSchema(SCHEMA);
        jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName(SCHEMA);
            Liquibase liquibase = new Liquibase(CHANGELOG, new ClassLoaderResourceAccessor(), database);
            int total = liquibase.getDatabaseChangeLog().getChangeSets().size();
            liquibase.update(total - 1, "");
            assertThat(jdbc.queryForObject(
                    "select count(*) from databasechangelog where id = '047-tenant-calendar-entries'", Integer.class)).isZero();
            jdbc.update("""
                    insert into tenant_calendar (tenant_id, zone_id, weekend, holidays, updated_at, updated_by)
                    values ('t1', 'Europe/Brussels', 'SATURDAY_SUNDAY', '2026-11-11,2026-12-25, ,2026-11-11', now(), 'admin'),
                           ('t2', 'UTC', 'SUNDAY_ONLY', '', now(), 'admin'),
                           ('t3', 'UTC', 'SATURDAY_SUNDAY', '2026-05-01', now(), 'admin')
                    """);
            liquibase.update("");
        }
    }

    @AfterEach
    void tearDown() {
        dataSource.close();
        EmbeddedPostgresSupport.dropSchema(SCHEMA);
    }

    @Test
    void legacyDatesBecomeManualClosedOneDayEntriesWithoutLabel_duplicatesAndBlanksDropped() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select tenant_id, source_id, start_date, end_date, nature, label
                from tenant_calendar_entry order by tenant_id, start_date
                """);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(row -> row.get("tenant_id")).containsExactly("t1", "t1", "t3");
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("source_id")).isNull();
            assertThat(row.get("nature")).isEqualTo("CLOSED");
            assertThat(row.get("label")).isNull();
            assertThat(row.get("start_date")).isEqualTo(row.get("end_date"));
        });
        assertThat(rows.get(0).get("start_date").toString()).isEqualTo("2026-11-11");
        assertThat(rows.get(1).get("start_date").toString()).isEqualTo("2026-12-25");
        assertThat(rows.get(2).get("start_date").toString()).isEqualTo("2026-05-01");
    }

    @Test
    void theHolidaysColumnIsGone_andTheSettingsSurvive() {
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = ? and table_name = 'tenant_calendar' and column_name = 'holidays'
                """, Integer.class, SCHEMA)).isZero();
        assertThat(jdbc.queryForObject("select zone_id from tenant_calendar where tenant_id = 't1'", String.class))
                .isEqualTo("Europe/Brussels");
        assertThat(jdbc.queryForObject("select weekend from tenant_calendar where tenant_id = 't2'", String.class))
                .isEqualTo("SUNDAY_ONLY");
    }

    @Test
    void aSourceNameIsUniquePerTenantIgnoringCase_butFreeAcrossTenants() {
        insertSource("t1", "Fériés");

        assertThatThrownBy(() -> insertSource("t1", "FÉRIÉS")).isInstanceOf(DataIntegrityViolationException.class);
        insertSource("t2", "Fériés");
    }

    @Test
    void anEntryCannotEndBeforeItStarts_norHaveAnUnknownNature() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into tenant_calendar_entry (id, tenant_id, start_date, end_date, nature)
                values (?, 't1', '2026-12-31', '2026-12-24', 'CLOSED')
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into tenant_calendar_entry (id, tenant_id, start_date, end_date, nature)
                values (?, 't1', '2026-12-24', '2026-12-24', 'MAYBE')
                """, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingASourceDeletesItsEntriesButNotTheManualOnes() {
        UUID source = insertSource("t1", "Fériés");
        jdbc.update("""
                insert into tenant_calendar_entry (id, tenant_id, source_id, start_date, end_date, nature)
                values (?, 't1', ?, '2026-12-01', '2026-12-01', 'CLOSED')
                """, UUID.randomUUID(), source);

        jdbc.update("delete from tenant_calendar_source where id = ?", source);

        assertThat(jdbc.queryForObject("select count(*) from tenant_calendar_entry where source_id = ?", Integer.class, source)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from tenant_calendar_entry where tenant_id = 't1'", Integer.class)).isEqualTo(2);
    }

    private UUID insertSource(String tenant, String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenant_calendar_source (id, tenant_id, name, kind) values (?, ?, ?, 'ICS_FILE')", id, tenant, name);
        return id;
    }
}
