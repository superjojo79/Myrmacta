package be.myrmacta.domain.deadline.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * TODO §120 (D-CAL-COUNT, D-CAL-ZONE, D-CAL-TIMER) — jour civil du fuseau, avance et recul calendaires ou ouvrables.
 * Le jour de départ ne compte pas. Un jour férié qui tombe le week-end n'est qu'un jour fermé.
 */
public final class BusinessCalendar {

    /** Au-delà, un décompte qui ne trouve pas assez de jours ouvrables échoue plutôt que de boucler. */
    public static final int MAX_STEPS = 8_000;

    private final ZoneId zone;
    private final WeekendPattern weekend;
    private final Set<LocalDate> holidays;

    public BusinessCalendar(ZoneId zone, WeekendPattern weekend, Set<LocalDate> holidays) {
        this.zone = Objects.requireNonNull(zone, "zone");
        this.weekend = Objects.requireNonNull(weekend, "weekend");
        this.holidays = Set.copyOf(Objects.requireNonNull(holidays, "holidays"));
    }

    public static BusinessCalendar defaults() {
        return new BusinessCalendar(ZoneId.of("UTC"), WeekendPattern.SATURDAY_SUNDAY, Set.of());
    }

    public ZoneId zone() {
        return zone;
    }

    public WeekendPattern weekend() {
        return weekend;
    }

    public Set<LocalDate> holidays() {
        return holidays;
    }

    public LocalDate today(Instant instant) {
        return LocalDate.ofInstant(Objects.requireNonNull(instant, "instant"), zone);
    }

    public boolean isBusinessDay(LocalDate date) {
        return !weekend.isWeekend(date) && !holidays.contains(date);
    }

    /** {@code days} vaut 0 : la date de départ. Négatif refusé. */
    public LocalDate add(LocalDate start, int days, DayCount count) {
        return move(start, days, count, 1);
    }

    public LocalDate subtract(LocalDate start, int days, DayCount count) {
        return move(start, days, count, -1);
    }

    /** Premier jour où le terme est dépassé. En ouvrable, le prochain jour ouvrable après le terme. */
    public LocalDate firstOverdueDay(LocalDate deadline, DayCount count) {
        Objects.requireNonNull(count, "count");
        if (count == DayCount.CALENDAR) {
            return deadline.plusDays(1);
        }
        return add(deadline, 1, DayCount.BUSINESS);
    }

    public boolean isOverdue(LocalDate today, LocalDate deadline, DayCount count) {
        return !today.isBefore(firstOverdueDay(deadline, count));
    }

    /** Minuit du fuseau, en instant. */
    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    public ZonedDateTime atStartOfDay(LocalDate date) {
        return date.atStartOfDay(zone);
    }

    private LocalDate move(LocalDate start, int days, DayCount count, int direction) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(count, "count");
        if (days < 0) {
            throw new IllegalArgumentException("days must be >= 0");
        }
        if (days == 0 || count == DayCount.CALENDAR) {
            return direction > 0 ? start.plusDays(days) : start.minusDays(days);
        }
        LocalDate cursor = start;
        int remaining = days;
        for (int step = 0; remaining > 0; step++) {
            if (step >= MAX_STEPS) {
                throw new IllegalArgumentException("no " + days + " business days within " + MAX_STEPS + " days of " + start);
            }
            cursor = cursor.plusDays(direction);
            if (isBusinessDay(cursor)) {
                remaining--;
            }
        }
        return cursor;
    }
}
