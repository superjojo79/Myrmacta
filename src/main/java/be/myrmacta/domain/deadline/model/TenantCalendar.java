package be.myrmacta.domain.deadline.model;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND, D-CAL-HOLIDAYS) — réglage du tenant. Absent en base : {@link #defaults()}. */
public record TenantCalendar(String zoneId, WeekendPattern weekend, List<LocalDate> holidays) {

    public static final int MAX_HOLIDAYS = 400;
    public static final int MAX_ZONE_LENGTH = 64;

    public TenantCalendar {
        zoneId = canonicalZone(zoneId);
        Objects.requireNonNull(weekend, "weekend");
        if (holidays == null) {
            holidays = List.of();
        }
        Set<LocalDate> unique = new LinkedHashSet<>();
        for (LocalDate holiday : holidays) {
            if (holiday == null) {
                throw new IllegalArgumentException("holiday date is required");
            }
            unique.add(holiday);
        }
        if (unique.size() > MAX_HOLIDAYS) {
            throw new IllegalArgumentException("at most " + MAX_HOLIDAYS + " holidays");
        }
        holidays = unique.stream().sorted().toList();
    }

    public static TenantCalendar defaults() {
        return new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of());
    }

    public BusinessCalendar toCalendar() {
        return new BusinessCalendar(ZoneId.of(zoneId), weekend, Set.copyOf(holidays));
    }

    /** Ajoute des dates sans retirer celles déjà présentes. Refuse si le total dépasse le plafond. */
    public TenantCalendar plus(List<LocalDate> added) {
        List<LocalDate> merged = new ArrayList<>(holidays);
        merged.addAll(added);
        return new TenantCalendar(zoneId, weekend, merged);
    }

    private static String canonicalZone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("time zone is required");
        }
        String id = raw.strip();
        if (id.length() > MAX_ZONE_LENGTH) {
            throw new IllegalArgumentException("time zone is too long");
        }
        try {
            return ZoneId.of(id).getId();
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("unknown time zone: " + id);
        }
    }
}
