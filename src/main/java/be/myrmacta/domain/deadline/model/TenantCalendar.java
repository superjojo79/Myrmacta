package be.myrmacta.domain.deadline.model;

import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * TODO §120 (D-CAL-ZONE, D-CAL-WEEKEND) et §121 (D-CAL-ENTRY, D-CAL-SOURCE, D-CAL-OVERRIDE) — réglage du tenant :
 * fuseau, week-end, saisies manuelles et sources. Absent en base : {@link #defaults()}.
 */
public record TenantCalendar(String zoneId, WeekendPattern weekend, List<CalendarEntry> manual, List<CalendarSource> sources) {

    public static final int MAX_MANUAL = 2_000;
    public static final int MAX_SOURCES = 10;
    public static final int MAX_ZONE_LENGTH = 64;
    /** Garde-fou ajouté en §121 : borne le nombre de jours que {@link #toCalendar()} déroule en mémoire. */
    public static final int MAX_EXPANDED_DAYS = 200_000;

    public TenantCalendar {
        zoneId = canonicalZone(zoneId);
        Objects.requireNonNull(weekend, "weekend");
        manual = manual == null ? List.of() : manual.stream().peek(Objects::requireNonNull).sorted().toList();
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (manual.size() > MAX_MANUAL) {
            throw new IllegalArgumentException("at most " + MAX_MANUAL + " manual entries");
        }
        if (sources.size() > MAX_SOURCES) {
            throw new IllegalArgumentException("at most " + MAX_SOURCES + " sources");
        }
        Set<String> names = new HashSet<>();
        Set<UUID> ids = new HashSet<>();
        long expanded = manual.stream().mapToLong(CalendarEntry::dayCount).sum();
        for (CalendarSource source : sources) {
            if (!names.add(source.nameKey())) {
                throw new IllegalArgumentException("source name already used: " + source.name());
            }
            if (!ids.add(source.id())) {
                throw new IllegalArgumentException("duplicate source id");
            }
            expanded += source.closedDays().stream().mapToLong(CalendarEntry::dayCount).sum();
        }
        if (expanded > MAX_EXPANDED_DAYS) {
            throw new IllegalArgumentException("the calendar spans too many days (at most " + MAX_EXPANDED_DAYS + ")");
        }
    }

    public static TenantCalendar defaults() {
        return new TenantCalendar("UTC", WeekendPattern.SATURDAY_SUNDAY, List.of(), List.of());
    }

    /** Jours fermés effectifs, triés : sources et saisies {@code CLOSED}, moins les jours ouvrés forcés. */
    public List<LocalDate> effectiveClosedDays() {
        Set<LocalDate> closed = new TreeSet<>(closedDays());
        closed.removeAll(forcedOpenDays());
        return List.copyOf(closed);
    }

    public BusinessCalendar toCalendar() {
        return new BusinessCalendar(ZoneId.of(zoneId), weekend, closedDays(), forcedOpenDays());
    }

    public TenantCalendar withSettings(String newZoneId, WeekendPattern newWeekend, List<CalendarEntry> newManual) {
        return new TenantCalendar(newZoneId, newWeekend, newManual, sources);
    }

    /** Remplace la source de même identifiant, sinon l'ajoute. */
    public TenantCalendar withSource(CalendarSource source) {
        List<CalendarSource> next = new java.util.ArrayList<>(sources.stream().filter(s -> !s.id().equals(source.id())).toList());
        next.add(source);
        return new TenantCalendar(zoneId, weekend, manual, next);
    }

    public TenantCalendar withoutSource(UUID id) {
        return new TenantCalendar(zoneId, weekend, manual, sources.stream().filter(s -> !s.id().equals(id)).toList());
    }

    private Set<LocalDate> closedDays() {
        Set<LocalDate> closed = new LinkedHashSet<>();
        for (CalendarSource source : sources) {
            source.closedDays().forEach(entry -> closed.addAll(entry.days()));
        }
        manual.stream().filter(entry -> entry.nature() == Nature.CLOSED).forEach(entry -> closed.addAll(entry.days()));
        return closed;
    }

    private Set<LocalDate> forcedOpenDays() {
        Set<LocalDate> open = new LinkedHashSet<>();
        manual.stream().filter(entry -> entry.nature() == Nature.OPEN).forEach(entry -> open.addAll(entry.days()));
        return open;
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
