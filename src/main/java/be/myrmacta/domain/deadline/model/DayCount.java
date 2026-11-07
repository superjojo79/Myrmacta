package be.myrmacta.domain.deadline.model;

import java.util.Locale;
import java.util.Optional;

/** TODO §120 (D-CAL-COUNT) — calendaire ou ouvrable. Absent vaut calendaire. */
public enum DayCount {
    CALENDAR,
    BUSINESS;

    /**
     * @return vide si la valeur est présente mais inconnue. {@code null}, vide, {@code false},
     *         {@code CALENDAIRE} et {@code CALENDAR} valent {@link #CALENDAR}.
     */
    public static Optional<DayCount> tryParse(Object raw) {
        if (raw == null) {
            return Optional.of(CALENDAR);
        }
        if (raw instanceof Boolean business) {
            return Optional.of(business ? BUSINESS : CALENDAR);
        }
        if (raw instanceof DayCount count) {
            return Optional.of(count);
        }
        String text = raw.toString().strip();
        if (text.isEmpty()) {
            return Optional.of(CALENDAR);
        }
        return switch (text.toUpperCase(Locale.ROOT)) {
            case "CALENDAR", "CALENDAIRE", "FALSE" -> Optional.of(CALENDAR);
            case "BUSINESS", "OUVRABLE", "TRUE" -> Optional.of(BUSINESS);
            default -> Optional.empty();
        };
    }

    public static DayCount parse(Object raw) {
        return tryParse(raw).orElseThrow(() -> new IllegalArgumentException("unknown day count: " + raw));
    }
}
