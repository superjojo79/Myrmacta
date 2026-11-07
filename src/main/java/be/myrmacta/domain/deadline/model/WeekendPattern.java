package be.myrmacta.domain.deadline.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Locale;

/** TODO §120 (D-CAL-WEEKEND) — les trois week-ends admis. Le défaut est samedi et dimanche. */
public enum WeekendPattern {
    SATURDAY_SUNDAY,
    FRIDAY_SATURDAY,
    SUNDAY_ONLY;

    public boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return switch (this) {
            case SATURDAY_SUNDAY -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
            case FRIDAY_SATURDAY -> day == DayOfWeek.FRIDAY || day == DayOfWeek.SATURDAY;
            case SUNDAY_ONLY -> day == DayOfWeek.SUNDAY;
        };
    }

    public static WeekendPattern parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("weekend is required");
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("unknown weekend: " + raw.strip());
        }
    }
}
