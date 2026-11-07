package be.myrmacta.domain.deadline.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** TODO §121 (D-CAL-ICS-PREVIEW) — suite de jours consécutifs, bornes incluses, pour résumer un aperçu. */
public record DayRange(LocalDate start, LocalDate end) {

    public DayRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    /** Regroupe des jours, dans le désordre et éventuellement répétés, en suites de jours consécutifs triées. */
    public static List<DayRange> compress(Collection<LocalDate> days) {
        List<DayRange> ranges = new ArrayList<>();
        LocalDate start = null;
        LocalDate previous = null;
        for (LocalDate day : new TreeSet<>(days)) {
            if (previous != null && !day.equals(previous.plusDays(1))) {
                ranges.add(new DayRange(start, previous));
                start = null;
            }
            if (start == null) {
                start = day;
            }
            previous = day;
        }
        if (start != null) {
            ranges.add(new DayRange(start, previous));
        }
        return List.copyOf(ranges);
    }
}
