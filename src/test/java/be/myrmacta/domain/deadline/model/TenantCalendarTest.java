package be.myrmacta.domain.deadline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** TODO §121 (D-CAL-ENTRY, D-CAL-SOURCE, D-CAL-OVERRIDE) — plafonds, unicité des sources, jours effectifs. */
class TenantCalendarTest {

    private static final LocalDate MONDAY = LocalDate.parse("2026-11-09");

    private static CalendarSource source(String name, CalendarEntry... days) {
        return new CalendarSource(UUID.randomUUID(), name, CalendarSource.Kind.ICS_FILE, List.of(days),
                Instant.parse("2026-11-07T10:00:00Z"), "admin", "abc", null);
    }

    @Test
    void defaultsAreUtcSaturdaySundayAndEmpty() {
        TenantCalendar calendar = TenantCalendar.defaults();

        assertThat(calendar.zoneId()).isEqualTo("UTC");
        assertThat(calendar.weekend()).isEqualTo(WeekendPattern.SATURDAY_SUNDAY);
        assertThat(calendar.manual()).isEmpty();
        assertThat(calendar.sources()).isEmpty();
        assertThat(calendar.effectiveClosedDays()).isEmpty();
    }

    @Test
    void manualEntriesAreSortedByStart() {
        TenantCalendar calendar = new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY,
                List.of(CalendarEntry.day(MONDAY.plusDays(5), Nature.CLOSED, "b"), CalendarEntry.day(MONDAY, Nature.CLOSED, "a")),
                List.of());

        assertThat(calendar.manual()).extracting(CalendarEntry::label).containsExactly("a", "b");
    }

    @Test
    void atMost2000ManualEntries() {
        List<CalendarEntry> entries = new ArrayList<>();
        for (int i = 0; i < TenantCalendar.MAX_MANUAL; i++) {
            entries.add(CalendarEntry.day(MONDAY.plusDays(i), Nature.CLOSED, null));
        }
        assertThat(new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, entries, List.of()).manual()).hasSize(2000);

        entries.add(CalendarEntry.day(MONDAY.minusDays(1), Nature.CLOSED, null));
        assertThatThrownBy(() -> new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, entries, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000");
    }

    @Test
    void atMostTenSources_withUniqueNamesIgnoringCase() {
        List<CalendarSource> ten = new ArrayList<>();
        for (int i = 0; i < TenantCalendar.MAX_SOURCES; i++) {
            ten.add(source("Source " + i));
        }
        assertThat(new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(), ten).sources()).hasSize(10);

        ten.add(source("Source 10"));
        assertThatThrownBy(() -> new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(), ten))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(),
                List.of(source("Fériés"), source("FÉRIÉS"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aSourceNameIsRequiredAndCappedAt80Characters() {
        assertThat(source("x".repeat(80)).name()).hasSize(80);
        assertThatThrownBy(() -> source("x".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> source("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSourceOnlyProducesClosedDays() {
        assertThatThrownBy(() -> source("Fériés", CalendarEntry.day(MONDAY, Nature.OPEN, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CLOSED");
    }

    @Test
    void effectiveClosedDaysAreSourcesPlusManualClosed_minusForcedOpen() {
        CalendarSource holidays = source("Fériés",
                CalendarEntry.day(MONDAY, Nature.CLOSED, "A"),
                new CalendarEntry(MONDAY.plusDays(1), MONDAY.plusDays(2), Nature.CLOSED, "B"));
        TenantCalendar calendar = new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY,
                List.of(CalendarEntry.day(MONDAY.plusDays(10), Nature.CLOSED, "manuel"),
                        CalendarEntry.day(MONDAY.plusDays(1), Nature.OPEN, "travaillé")),
                List.of(holidays));

        assertThat(calendar.effectiveClosedDays())
                .containsExactly(MONDAY, MONDAY.plusDays(2), MONDAY.plusDays(10));
    }

    @Test
    void toCalendarAppliesTheOverrideOrder() {
        CalendarSource holidays = source("Fériés", CalendarEntry.day(MONDAY, Nature.CLOSED, "A"));
        TenantCalendar calendar = new TenantCalendar("Europe/Brussels", WeekendPattern.SATURDAY_SUNDAY,
                List.of(CalendarEntry.day(LocalDate.parse("2026-11-14"), Nature.OPEN, "samedi travaillé")),
                List.of(holidays));

        BusinessCalendar business = calendar.toCalendar();

        assertThat(business.isBusinessDay(MONDAY)).isFalse();
        assertThat(business.isBusinessDay(LocalDate.parse("2026-11-14"))).isTrue();
        assertThat(business.isBusinessDay(LocalDate.parse("2026-11-15"))).isFalse();
    }

    @Test
    void withSourceReplacesTheSameIdOrAppends() {
        CalendarSource first = source("Fériés", CalendarEntry.day(MONDAY, Nature.CLOSED, "A"));
        TenantCalendar calendar = TenantCalendar.defaults().withSource(first);
        CalendarSource reloaded = first.reloaded(List.of(CalendarEntry.day(MONDAY.plusDays(1), Nature.CLOSED, "B")),
                Instant.parse("2026-11-08T10:00:00Z"), "other", "def");

        TenantCalendar after = calendar.withSource(reloaded);

        assertThat(after.sources()).hasSize(1);
        assertThat(after.sources().getFirst().id()).isEqualTo(first.id());
        assertThat(after.effectiveClosedDays()).containsExactly(MONDAY.plusDays(1));
        assertThat(after.withoutSource(first.id()).sources()).isEmpty();
    }

    // ------------------------------------------------------------------ source par URL (lot 3)

    @Test
    void aSourceKeepsAMaskedUrlOnlyWhenItIsAnUrlSource() {
        CalendarSource url = new CalendarSource(UUID.randomUUID(), "Écoles", CalendarSource.Kind.ICS_URL, List.of(), Instant.parse("2026-11-07T10:00:00Z"),
                "admin", "abc", null, "https://calendar.example.org/…");

        assertThat(url.maskedUrl()).isEqualTo("https://calendar.example.org/…");
        assertThatThrownBy(() -> new CalendarSource(UUID.randomUUID(), "Fichier", CalendarSource.Kind.ICS_FILE, List.of(), null, null, null, null,
                "https://calendar.example.org/…")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CalendarSource(UUID.randomUUID(), "Belge", CalendarSource.Kind.BE_FEDERAL, List.of(), null, null, null, null,
                "https://calendar.example.org/…")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUrlSourceMayHaveNoMaskedUrl_whichMeansTheUrlMustBeEnteredAgain() {
        CalendarSource imported = new CalendarSource(UUID.randomUUID(), "Écoles", CalendarSource.Kind.ICS_URL, List.of(), null, "promotion", null, null);

        assertThat(imported.maskedUrl()).isNull();
    }

    @Test
    void reloadingKeepsTheMaskedUrlUnlessAnotherIsGiven() {
        CalendarSource url = CalendarSource.create("Écoles", CalendarSource.Kind.ICS_URL, List.of(), Instant.parse("2026-11-07T10:00:00Z"), "admin", "abc")
                .withMaskedUrl("https://a.example.org/…");

        CalendarSource same = url.reloaded(List.of(), Instant.parse("2026-11-08T10:00:00Z"), "other", "def");
        CalendarSource moved = same.withMaskedUrl("https://b.example.org/…");

        assertThat(same.maskedUrl()).isEqualTo("https://a.example.org/…");
        assertThat(moved.maskedUrl()).isEqualTo("https://b.example.org/…");
        assertThat(url.failed("boom").maskedUrl()).isEqualTo("https://a.example.org/…");
    }

    @Test
    void aFailedLoadKeepsTheDaysAndRecordsTheErrorCappedAt500Characters_thenAReloadClearsIt() {
        CalendarSource url = CalendarSource.create("Écoles", CalendarSource.Kind.ICS_URL, List.of(CalendarEntry.day(MONDAY, Nature.CLOSED, "A")),
                Instant.parse("2026-11-07T10:00:00Z"), "admin", "abc");

        CalendarSource failed = url.failed("x".repeat(900));

        assertThat(failed.lastError()).hasSize(500);
        assertThat(failed.closedDays()).isEqualTo(url.closedDays());
        assertThat(failed.contentSha256()).isEqualTo("abc");
        assertThat(failed.reloaded(List.of(), Instant.parse("2026-11-09T10:00:00Z"), "admin", "def").lastError()).isNull();
    }
}
