package be.myrmacta.infrastructure.persistence.deadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.WeekendPattern;
import be.myrmacta.domain.deadline.port.in.ManageTenantCalendarUseCase;
import be.myrmacta.domain.deadline.port.out.TenantCalendarRepository;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.support.OidcClientTestConfiguration;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * TODO §121 (revue de #33) — sur une vraie base : les écritures de deux administrateurs se suivent sans rien perdre,
 * les plafonds tiennent sous concurrence, un secret ne survit pas à un changement de type, une source d'un autre
 * tenant n'est jamais touchée.
 */
@SpringBootTest
@Import(OidcClientTestConfiguration.class)
class JdbcTenantCalendarIT {

    private static final String TENANT = "tenant_dev";
    private static final String OTHER = "tenant_autre";
    private static final AccessContext.User ADMIN = new AccessContext.User("admin-it", Set.of("TENANT_ADMIN"));

    @Autowired
    private ManageTenantCalendarUseCase useCase;

    @Autowired
    private TenantCalendarRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private ExecutorService pool;

    @BeforeEach
    void clean() {
        pool = Executors.newFixedThreadPool(2);
        wipe();
    }

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
        wipe();
    }

    private void wipe() {
        for (String table : new String[] {"tenant_calendar_entry", "tenant_calendar_source", "tenant_calendar"}) {
            jdbc.update("delete from " + table + " where tenant_id in (?, ?)", TENANT, OTHER);
        }
    }

    private static byte[] ics(String date, String summary) {
        return ("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//IT//FR\r\nBEGIN:VEVENT\r\nUID:" + UUID.randomUUID()
                + "\r\nDTSTAMP:20260101T000000Z\r\nDTSTART;VALUE=DATE:" + date.replace("-", "") + "\r\nSUMMARY:" + summary + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static CalendarSource source(String name, CalendarSource.Kind kind) {
        return CalendarSource.create(name, kind, List.of(CalendarEntry.day(LocalDate.parse("2026-12-25"), Nature.CLOSED, "Noël")),
                Instant.parse("2026-11-07T10:00:00Z"), "admin-it", "sha");
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    /** Lance les deux tâches au même instant et rend leur issue : le résultat, ou l'exception. */
    private List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : List.of(first, second)) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (ExecutionException failed) {
                outcomes.add(failed.getCause());
            }
        }
        return outcomes;
    }

    private Callable<Object> create(String name, byte[] content) {
        String sha = useCase.previewFileSource(ADMIN, name, null, content).sha256();
        return () -> useCase.applyFileSource(ADMIN, name, null, content, sha);
    }

    @Test
    void twoAdministratorsCreatingTheTenthSourceAtOnce_oneWins_andTheCalendarStaysReadable() throws Exception {
        for (int round = 0; round < 4; round++) {
            wipe();
            for (int i = 0; i < 9; i++) {
                useCase.applyFileSource(ADMIN, "Source " + i, null, ics("2026-12-2" + i, "S" + i),
                        useCase.previewFileSource(ADMIN, "Source " + i, null, ics("2026-12-2" + i, "S" + i)).sha256());
            }
            byte[] one = ics("2027-01-04", "Rentrée");
            byte[] two = ics("2027-01-05", "Reprise");

            List<Object> outcomes = race(create("Dixième A", one), create("Dixième B", two));

            long succeeded = outcomes.stream().filter(o -> !(o instanceof Throwable)).count();
            assertThat(succeeded).as("round %d: %s", round, outcomes).isEqualTo(1);
            assertThat(outcomes.stream().filter(o -> o instanceof Throwable).map(o -> (Throwable) o))
                    .allSatisfy(failure -> assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("at most"));
            assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT)).isEqualTo(10);
            assertThat(useCase.get(ADMIN).calendar().sources()).hasSize(10);
        }
    }

    @Test
    void twoAdministratorsCreatingTheSameNameAtOnce_oneWins_theOtherGetsAConflictNotAServerError() throws Exception {
        byte[] content = ics("2027-01-04", "Rentrée");

        List<Object> outcomes = race(create("Même nom", content), create("MÊME NOM", content));

        assertThat(outcomes.stream().filter(o -> !(o instanceof Throwable))).hasSize(1);
        assertThat(outcomes.stream().filter(o -> o instanceof Throwable).map(o -> (Throwable) o))
                .allSatisfy(failure -> assertThat(failure.getClass().getSimpleName()).isEqualTo("CalendarSourceConflictException"));
        assertThat(count("select count(*) from tenant_calendar_source where tenant_id = ?", TENANT)).isEqualTo(1);
    }

    @Test
    void aManualEditRacingASourceApplication_isNeverLost() throws Exception {
        for (int round = 0; round < 12; round++) {
            wipe();
            useCase.replace(ADMIN, "UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(CalendarEntry.day(LocalDate.parse("2026-12-01"), Nature.CLOSED, "Avant")));
            CalendarEntry edited = CalendarEntry.day(LocalDate.parse("2026-12-02").plusDays(round), Nature.CLOSED, "Saisie " + round);
            byte[] content = ics("2027-01-04", "Rentrée");
            Callable<Object> apply = create("Source " + round, content);

            race(() -> useCase.replace(ADMIN, "Europe/Brussels", WeekendPattern.SUNDAY_ONLY, List.of(edited)), apply);

            var calendar = useCase.get(ADMIN).calendar();
            assertThat(calendar.manual()).as("round %d", round).containsExactly(edited);
            assertThat(calendar.zoneId()).isEqualTo("Europe/Brussels");
            assertThat(calendar.weekend()).isEqualTo(WeekendPattern.SUNDAY_ONLY);
            assertThat(calendar.sources()).extracting(CalendarSource::name).containsExactly("Source " + round);
        }
    }

    @Test
    void lockedRollsEveryWriteBackWhenTheWorkFails() {
        CalendarSource lost = source("Perdue", CalendarSource.Kind.ICS_FILE);

        assertThatThrownBy(() -> repository.locked(TENANT, () -> {
            repository.saveSource(TENANT, lost, null);
            throw new IllegalStateException("panne au milieu");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from tenant_calendar_source where id = ?", lost.id())).isZero();
        assertThat(count("select count(*) from tenant_calendar_entry where source_id = ?", lost.id())).isZero();
    }

    @Test
    void touchNeverRewritesTheManualEntriesOrTheSources() {
        useCase.replace(ADMIN, "Europe/Brussels", WeekendPattern.SUNDAY_ONLY, List.of(CalendarEntry.day(LocalDate.parse("2026-12-01"), Nature.CLOSED, "Pont")));
        repository.saveSource(TENANT, source("Fériés", CalendarSource.Kind.ICS_FILE), null);

        repository.touch(TENANT, Instant.parse("2026-11-08T10:00:00Z"), "autre");

        var stored = repository.find(TENANT).orElseThrow();
        assertThat(stored.updatedBy()).isEqualTo("autre");
        assertThat(stored.calendar().manual()).hasSize(1);
        assertThat(stored.calendar().sources()).hasSize(1);
        assertThat(stored.calendar().zoneId()).isEqualTo("Europe/Brussels");
    }

    @Test
    void touchCreatesTheDefaultRowWhenTheTenantHasNone() {
        repository.touch(TENANT, Instant.parse("2026-11-08T10:00:00Z"), "admin-it");

        var stored = repository.find(TENANT).orElseThrow();
        assertThat(stored.calendar().zoneId()).isEqualTo("UTC");
        assertThat(stored.calendar().weekend()).isEqualTo(WeekendPattern.SATURDAY_SUNDAY);
    }

    @Test
    void anIdOwnedByAnotherTenantIsRefused_andWritesNoPeriodsNorTouchesTheOtherRow() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenant_calendar_source (id, tenant_id, name, kind) values (?, ?, 'Étrangère', 'ICS_FILE')", id, OTHER);
        CalendarSource clash = new CalendarSource(id, "Collision", CalendarSource.Kind.ICS_FILE,
                List.of(CalendarEntry.day(LocalDate.parse("2026-12-25"), Nature.CLOSED, "Noël")), Instant.parse("2026-11-07T10:00:00Z"), "admin-it", "sha", null);

        assertThatThrownBy(() -> repository.saveSource(TENANT, clash, null)).isInstanceOf(IllegalStateException.class);

        assertThat(count("select count(*) from tenant_calendar_entry where source_id = ?", id)).isZero();
        assertThat(jdbc.queryForObject("select name from tenant_calendar_source where id = ?", String.class, id)).isEqualTo("Étrangère");
    }

    @Test
    void aSourceThatStopsBeingAnUrlSourceForgetsItsEncryptedUrl() {
        UUID id = UUID.randomUUID();
        CalendarSource url = new CalendarSource(id, "Agenda", CalendarSource.Kind.ICS_URL, List.of(), Instant.parse("2026-11-07T10:00:00Z"), "admin-it", "sha",
                null, "https://calendar.example.org/…");
        repository.saveSource(TENANT, url, "https://calendar.example.org/ical/TOPSECRET/basic.ics");
        assertThat(repository.findSourceUrl(TENANT, id)).contains("https://calendar.example.org/ical/TOPSECRET/basic.ics");

        // Une promotion remplace la source par un fichier de même nom (même identifiant).
        repository.saveSource(TENANT, new CalendarSource(id, "Agenda", CalendarSource.Kind.ICS_FILE, List.of(), null, "promotion", null, null), null);

        assertThat(jdbc.queryForObject("select url_ciphertext from tenant_calendar_source where id = ?", String.class, id)).isNull();
        assertThat(repository.findSourceUrl(TENANT, id)).isEmpty();
    }

    @Test
    void anUrlSourceWithoutAMaskedUrlHasNoSecretEither_butOneWithItKeepsItOnAReload() {
        UUID id = UUID.randomUUID();
        CalendarSource withUrl = new CalendarSource(id, "Agenda", CalendarSource.Kind.ICS_URL, List.of(), null, "admin-it", null, null, "https://calendar.example.org/…");
        repository.saveSource(TENANT, withUrl, "https://calendar.example.org/ical/TOPSECRET/basic.ics");

        repository.saveSource(TENANT, withUrl.reloaded(List.of(), Instant.parse("2026-11-08T10:00:00Z"), "admin-it", "sha-2"), null);
        assertThat(repository.findSourceUrl(TENANT, id)).contains("https://calendar.example.org/ical/TOPSECRET/basic.ics");

        repository.saveSource(TENANT, withUrl.withMaskedUrl(null), null);
        assertThat(repository.findSourceUrl(TENANT, id)).isEmpty();
    }

    @Test
    void aStoredUrlThatCannotBeDecryptedIsTreatedAsMissing_notAsAnError() {
        UUID id = UUID.randomUUID();
        repository.saveSource(TENANT, new CalendarSource(id, "Agenda", CalendarSource.Kind.ICS_URL, List.of(), null, "admin-it", null, null,
                "https://calendar.example.org/…"), "https://calendar.example.org/ical/TOPSECRET/basic.ics");
        jdbc.update("update tenant_calendar_source set url_ciphertext = 'enc:v1:pas-du-chiffre' where id = ?", id);

        assertThat(repository.findSourceUrl(TENANT, id)).isEmpty();
        assertThat(repository.findSourceUrl(TENANT, UUID.randomUUID())).isEmpty();
    }

    @Test
    void theStoredUrlOfASourceOfAnotherTenantIsNeverRead() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into tenant_calendar_source (id, tenant_id, name, kind, url_ciphertext, masked_url) values (?, ?, 'Étrangère', 'ICS_URL', 'enc:v1:x', 'https://x.example/…')",
                id, OTHER);

        assertThat(repository.findSourceUrl(TENANT, id)).isEmpty();
    }
}
