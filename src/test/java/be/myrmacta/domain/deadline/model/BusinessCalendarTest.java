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
    void addingBelgianDatesKeepsExistingOnesAndRefusesPastTheCap() {
        TenantCalendar calendar = TenantCalendar.defaults().plus(List.of(LocalDate.parse("2026-05-02")));

        TenantCalendar filled = calendar.plus(BelgianFederalHolidays.of(2026, 2027));

        assertThat(filled.holidays()).contains(LocalDate.parse("2026-05-02"), LocalDate.parse("2026-04-06"));
        assertThat(filled.plus(BelgianFederalHolidays.of(2026)).holidays()).isEqualTo(filled.holidays());
        assertThatThrownBy(() -> TenantCalendar.defaults().plus(java.util.stream.IntStream.range(0, 401)
                .mapToObj(i -> LocalDate.of(2020, 1, 1).plusDays(i)).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownZoneOrDayCountIsRefused_andAbsenceMeansCalendar() {
        assertThatThrownBy(() -> new TenantCalendar("Mars/Phobos", WeekendPattern.SATURDAY_SUNDAY, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(DayCount.tryParse(null)).contains(DayCount.CALENDAR);
        assertThat(DayCount.tryParse("OUVRABLE")).contains(DayCount.BUSINESS);
        assertThat(DayCount.tryParse("parfois")).isEmpty();
    }
}
