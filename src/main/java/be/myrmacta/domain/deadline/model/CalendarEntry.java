package be.myrmacta.domain.deadline.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * TODO §121 (D-CAL-ENTRY) — une date ou une période du calendrier du tenant, bornes incluses. Un seul jour a
 * {@code end == start}. Une période reste une période : elle n'est jamais éclatée en jours à l'enregistrement.
 */
public record CalendarEntry(LocalDate start, LocalDate end, Nature nature, String label) implements Comparable<CalendarEntry> {

    public static final int MAX_DAYS = 366;
    public static final int MAX_LABEL = 120;

    public CalendarEntry {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(nature, "nature");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new IllegalArgumentException("a period spans at most " + MAX_DAYS + " days");
        }
        if (label != null) {
            label = label.strip();
            if (label.isEmpty()) {
                label = null;
            } else if (label.length() > MAX_LABEL) {
                throw new IllegalArgumentException("label is at most " + MAX_LABEL + " characters");
            }
        }
    }

    public static CalendarEntry day(LocalDate day, Nature nature, String label) {
        return new CalendarEntry(day, day, nature, label);
    }

    public int dayCount() {
        return (int) (ChronoUnit.DAYS.between(start, end) + 1);
    }

    public List<LocalDate> days() {
        return Stream.iterate(start, day -> !day.isAfter(end), day -> day.plusDays(1)).toList();
    }

    @Override
    public int compareTo(CalendarEntry other) {
        int byStart = start.compareTo(other.start);
        if (byStart != 0) {
            return byStart;
        }
        int byEnd = end.compareTo(other.end);
        return byEnd != 0 ? byEnd : nature.compareTo(other.nature);
    }

    /** {@code CLOSED} : jour fermé. {@code OPEN} : jour ouvré forcé, y compris un jour de week-end. */
    public enum Nature {
        CLOSED,
        OPEN;

        public static Nature parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("nature is required");
            }
            try {
                return valueOf(raw.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("unknown nature: " + raw.strip());
            }
        }
    }
}
