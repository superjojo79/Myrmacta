package be.myrmacta.domain.deadline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** TODO §120 — décompte ouvrable, fuseau, fériés fédéraux belges. */
class BusinessCalendarTest {

    private static final LocalDate FRIDAY = LocalDate.parse("2026-11-06");

    @Test
    void oneBusinessDayAfterFridayIsMonday_andOneCalendarDayIsSaturday() {
        BusinessCalendar calendar = BusinessCalendar.defaults();

        assertThat(calendar.add(FRIDAY, 1, DayCount.BUSINESS)).isEqualTo(LocalDate.parse("2026-11-09"));
        assertThat(calendar.add(FRIDAY, 1, DayCount.CALENDAR)).isEqualTo(LocalDate.parse("2026-11-07"));
    }

    @Test
    void aHolidayMondayDoesNotCount_andGoingBackwardSkipsTheSameDays() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY,
                Set.of(LocalDate.parse("2026-11-09")));

        assertThat(calendar.add(FRIDAY, 1, DayCount.BUSINESS)).isEqualTo(LocalDate.parse("2026-11-10"));
        assertThat(calendar.subtract(LocalDate.parse("2026-11-10"), 1, DayCount.BUSINESS)).isEqualTo(FRIDAY);
    }

    @Test
    void sundayOnlyWeekendCountsSaturday() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SUNDAY_ONLY, Set.of());

        assertThat(calendar.add(FRIDAY, 1, DayCount.BUSINESS)).isEqualTo(LocalDate.parse("2026-11-07"));
    }

    @Test
    void zeroDaysStaysOnTheStart_andANegativeCountIsRefused() {
        BusinessCalendar calendar = BusinessCalendar.defaults();

        assertThat(calendar.add(FRIDAY, 0, DayCount.BUSINESS)).isEqualTo(FRIDAY);
        assertThatThrownBy(() -> calendar.add(FRIDAY, -1, DayCount.CALENDAR)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void businessEscalationWaitsUntilTheNextBusinessDay_calendarEscalationIsTheNextDay() {
        BusinessCalendar calendar = BusinessCalendar.defaults();

        assertThat(calendar.isOverdue(LocalDate.parse("2026-11-07"), FRIDAY, DayCount.BUSINESS)).isFalse();
        assertThat(calendar.isOverdue(LocalDate.parse("2026-11-09"), FRIDAY, DayCount.BUSINESS)).isTrue();
        assertThat(calendar.isOverdue(LocalDate.parse("2026-11-07"), FRIDAY, DayCount.CALENDAR)).isTrue();
        assertThat(calendar.isOverdue(FRIDAY, FRIDAY, DayCount.CALENDAR)).isFalse();
    }

    @Test
    void todayFollowsTheTenantZone() {
        BusinessCalendar brussels = new BusinessCalendar(ZoneId.of("Europe/Brussels"), WeekendPattern.SATURDAY_SUNDAY, Set.of());

        assertThat(brussels.today(Instant.parse("2026-11-06T23:30:00Z"))).isEqualTo(LocalDate.parse("2026-11-07"));
        assertThat(BusinessCalendar.defaults().today(Instant.parse("2026-11-06T23:30:00Z"))).isEqualTo(FRIDAY);
    }

    @Test
    void overdueInstantIsMidnightInTheTenantZoneOnTheNextCalendarDay() {
        BusinessCalendar brussels = new BusinessCalendar(ZoneId.of("Europe/Brussels"), WeekendPattern.SATURDAY_SUNDAY, Set.of());

        assertThat(brussels.startOfDay(FRIDAY.plusDays(1))).isEqualTo(Instant.parse("2026-11-06T23:00:00Z"));
    }

    @Test
    void belgianFederalHolidaysOf2026IncludeEasterMondayAndSkipASecondRun() {
        assertThat(BelgianFederalHolidays.easterSunday(2026)).isEqualTo(LocalDate.parse("2026-04-05"));
        assertThat(BelgianFederalHolidays.of(2026)).contains(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-04-06"),
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-14"),
                LocalDate.parse("2026-05-25"),
                LocalDate.parse("2026-07-21"),
                LocalDate.parse("2026-12-25"));
    }

    @Test
    void belgianFederalEntriesAreLabeledClosedDaysOfBothYears() {
        List<CalendarEntry> entries = BelgianFederalHolidays.entries(2026, 2027);

        assertThat(entries).hasSize(20);
        assertThat(entries).allMatch(entry -> entry.nature() == CalendarEntry.Nature.CLOSED && entry.label() != null);
        assertThat(entries).anyMatch(entry -> entry.start().equals(LocalDate.parse("2026-04-06")) && entry.label().equals("Lundi de Pâques"));
        assertThat(entries).anyMatch(entry -> entry.start().equals(LocalDate.parse("2027-01-01")) && entry.label().equals("Jour de l'an"));
    }

    @Test
    void anUnknownZoneOrDayCountIsRefused_andAbsenceMeansCalendar() {
        assertThatThrownBy(() -> new TenantCalendar("Mars/Phobos", WeekendPattern.SATURDAY_SUNDAY, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DayCount.tryParse(null)).contains(DayCount.CALENDAR);
        assertThat(DayCount.tryParse("OUVRABLE")).contains(DayCount.BUSINESS);
        assertThat(DayCount.tryParse("parfois")).isEmpty();
    }

    // TODO §121 (D-CAL-OVERRIDE) — manuel OPEN > manuel CLOSED > source fermée > week-end.

    private static final LocalDate SATURDAY = LocalDate.parse("2026-11-07");
    private static final LocalDate MONDAY = LocalDate.parse("2026-11-09");

    @Test
    void aForcedOpenDayIsABusinessDayEvenOnAWeekend() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY, Set.of(), Set.of(SATURDAY));

        assertThat(calendar.isBusinessDay(SATURDAY)).isTrue();
        assertThat(calendar.add(FRIDAY, 1, DayCount.BUSINESS)).isEqualTo(SATURDAY);
    }

    @Test
    void aForcedOpenDayBeatsAClosedDay() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY,
                Set.of(MONDAY), Set.of(MONDAY));

        assertThat(calendar.isBusinessDay(MONDAY)).isTrue();
        assertThat(calendar.add(LocalDate.parse("2026-11-08"), 1, DayCount.BUSINESS)).isEqualTo(MONDAY);
    }

    @Test
    void aClosedPeriodClosesEveryDayAndTheCountCrossesIt() {
        Set<LocalDate> closed = Set.of(MONDAY, MONDAY.plusDays(1), MONDAY.plusDays(2));
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY, closed, Set.of());

        assertThat(calendar.add(FRIDAY, 1, DayCount.BUSINESS)).isEqualTo(MONDAY.plusDays(3));
        assertThat(calendar.subtract(MONDAY.plusDays(3), 1, DayCount.BUSINESS)).isEqualTo(FRIDAY);
    }

    @Test
    void overdueFollowsTheOverrides() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY, Set.of(), Set.of(SATURDAY));

        // Le samedi travaillé est le premier jour ouvrable après le vendredi : l'escalade part dès le samedi.
        assertThat(calendar.firstOverdueDay(FRIDAY, DayCount.BUSINESS)).isEqualTo(SATURDAY);
        assertThat(calendar.isOverdue(SATURDAY, FRIDAY, DayCount.BUSINESS)).isTrue();
    }

    @Test
    void theCalendarKeepsTheSetsItWasGiven() {
        BusinessCalendar calendar = new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY,
                Set.of(MONDAY), Set.of(SATURDAY));

        assertThat(calendar.holidays()).containsExactly(MONDAY);
        assertThat(calendar.forcedOpen()).containsExactly(SATURDAY);
    }
}
