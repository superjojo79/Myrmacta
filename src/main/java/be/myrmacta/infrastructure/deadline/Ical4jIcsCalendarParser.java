package be.myrmacta.infrastructure.deadline;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarSource;
import be.myrmacta.domain.deadline.model.IcsIgnoreReason;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Range;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.transform.recurrence.Frequency;

/**
 * TODO §121 (D-CAL-ICS) — lecture des calendriers ICS avec ical4j 4.x. Seules les journées entières ({@code VALUE=DATE})
 * deviennent des jours fermés, {@code DTEND} étant exclusif. Aucun appel réseau (voir {@code ical4j.properties}) ; la
 * taille du fichier, le nombre d'événements et le nombre de périodes produites sont bornés.
 */
@org.springframework.stereotype.Component
class Ical4jIcsCalendarParser implements IcsCalendarParser {

    /**
     * Budget de temps de la lecture (seconde revue de #33). Une récurrence qui part de l'an 1 coûte environ 64 ms par
     * événement à dérouler jusqu'à la fenêtre sans produire de période : 5 000 événements tiendraient sous 1 Mio et
     * occuperaient un fil cinq minutes. Vérifié avant chaque événement : la lecture s'arrête au plus un événement après.
     */
    static final Duration DEFAULT_BUDGET = Duration.ofSeconds(5);

    private final long budgetNanos;

    Ical4jIcsCalendarParser() {
        this(DEFAULT_BUDGET);
    }

    Ical4jIcsCalendarParser(Duration budget) {
        this.budgetNanos = budget.toNanos();
    }

    @Override
    public ParsedIcs parse(byte[] content, ZoneId zone, int fromYear, int toYear) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("calendar file is empty");
        }
        if (content.length > MAX_BYTES) {
            throw new IllegalArgumentException("calendar file is over 1 MiB");
        }
        Objects.requireNonNull(zone, "zone");
        Calendar calendar = read(content);
        List<VEvent> events = calendar.getComponents(Component.VEVENT);
        if (events.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("calendar has more than " + MAX_EVENTS + " events");
        }
        // Les bornes d'un Period d'ical4j sont incluses : la fenêtre finit le 31 décembre de la dernière année, sinon une règle
        // au 1er janvier produirait une occurrence de l'année suivante.
        Period<LocalDate> window = new Period<>(LocalDate.of(fromYear, 1, 1), LocalDate.of(toYear, 12, 31));
        Map<IcsIgnoreReason, Integer> ignored = new EnumMap<>(IcsIgnoreReason.class);
        List<CalendarEntry> entries = new ArrayList<>();
        Map<String, SeriesOverride> overridden = overriddenOccurrences(events, zone);
        long deadline = System.nanoTime() + budgetNanos;
        for (VEvent event : events) {
            if (System.nanoTime() > deadline) {
                throw new IllegalArgumentException("calendar file is too expensive to read (more than "
                        + Duration.ofNanos(budgetNanos).toSeconds() + " s): recurrences starting very long ago cannot be unrolled");
            }
            try {
                Predicate<LocalDate> skipped = event.getProperty(Property.RECURRENCE_ID).isPresent()
                        ? day -> false
                        : overridden.getOrDefault(uidOf(event), SeriesOverride.NONE)::skips;
                IcsIgnoreReason reason = unfold(event, window, skipped, entries);
                if (reason != null) {
                    ignored.merge(reason, 1, Integer::sum);
                }
            } catch (RuntimeException invalid) {
                ignored.merge(IcsIgnoreReason.INVALID, 1, Integer::sum);
            }
            if (entries.size() > CalendarSource.MAX_ENTRIES) {
                throw new IllegalArgumentException("calendar holds more than " + CalendarSource.MAX_ENTRIES + " periods");
            }
        }
        entries.sort(null);
        return new ParsedIcs(entries, ignored, fingerprint(entries));
    }

    private static Calendar read(byte[] content) {
        try {
            return new CalendarBuilder().build(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));
        } catch (Exception | StackOverflowError unreadable) {
            throw new IllegalArgumentException("calendar file is not a readable iCalendar file");
        }
    }

    /** Ajoute les périodes de l'événement ; rend la raison pour laquelle il est écarté, nul s'il est retenu. */
    private static IcsIgnoreReason unfold(VEvent event, Period<LocalDate> window, Predicate<LocalDate> skipped,
                                          Collection<CalendarEntry> into) {
        if (event.getProperty(Property.STATUS).map(Property::getValue).filter("CANCELLED"::equalsIgnoreCase).isPresent()) {
            return IcsIgnoreReason.CANCELLED;
        }
        if (event.getTransparency().map(Property::getValue).filter("TRANSPARENT"::equalsIgnoreCase).isPresent()) {
            return IcsIgnoreReason.TRANSPARENT;
        }
        Optional<DtStart<Temporal>> start = event.getStartDate();
        if (start.isEmpty()) {
            return IcsIgnoreReason.INVALID;
        }
        if (!(start.get().getDate() instanceof LocalDate first)) {
            return IcsIgnoreReason.TIMED;
        }
        LocalDate endExclusive = first.plusDays(1);
        Optional<DtEnd<Temporal>> end = event.getEndDate();
        if (end.isPresent()) {
            if (!(end.get().getDate() instanceof LocalDate last)) {
                return IcsIgnoreReason.INVALID;
            }
            endExclusive = last;
        }
        if (!endExclusive.isAfter(first)) {
            return IcsIgnoreReason.INVALID;
        }
        String label = event.getProperty(Property.SUMMARY).map(Property::getValue).orElse(null);
        label = label == null ? null : truncate(label.strip());
        List<RRule<Temporal>> rules = event.getProperties(Property.RRULE);
        List<RDate<Temporal>> dates = event.getProperties(Property.RDATE);
        if (rules.isEmpty() && dates.isEmpty()) {
            if (!skipped.test(first)) {
                into.add(new CalendarEntry(first, endExclusive.minusDays(1), CalendarEntry.Nature.CLOSED, label));
            }
            return null;
        }
        for (RRule<Temporal> rule : rules) {
            Recur<Temporal> recur = rule.getRecur();
            if (recur.getFrequency() == Frequency.SECONDLY || recur.getFrequency() == Frequency.MINUTELY
                    || recur.getFrequency() == Frequency.HOURLY) {
                return IcsIgnoreReason.INVALID;
            }
        }
        for (Period<LocalDate> occurrence : event.<LocalDate>calculateRecurrenceSet(window)) {
            if (skipped.test(occurrence.getStart())) {
                continue;
            }
            LocalDate occurrenceEnd = occurrence.getEnd() == null || !occurrence.getEnd().isAfter(occurrence.getStart())
                    ? occurrence.getStart().plusDays(1) : occurrence.getEnd();
            // ical4j rend aussi une occurrence dont la fin (exclusive) touche le début de la fenêtre sans la recouvrir : on ne
            // garde que celles qui recouvrent un jour de la fenêtre (un congé commencé avant elle y reste).
            if (occurrenceEnd.minusDays(1).isBefore(window.getStart()) || occurrence.getStart().isAfter(window.getEnd())) {
                continue;
            }
            into.add(new CalendarEntry(occurrence.getStart(), occurrenceEnd.minusDays(1), CalendarEntry.Nature.CLOSED, label));
            if (into.size() > CalendarSource.MAX_ENTRIES) {
                return null;
            }
        }
        return null;
    }

    /**
     * TODO §121 (revue de #33) et §124 (D-CAL-THISANDFUTURE) — {@code RECURRENCE-ID} : un VEVENT de même UID qu'une série
     * en remplace une occurrence (annulée, déplacée ou rendue transparente). Avec {@code RANGE=THISANDFUTURE}, toutes
     * les occurrences à partir de cette date quittent la série. Le VEVENT de remplacement est lu à part.
     */
    private static Map<String, SeriesOverride> overriddenOccurrences(List<VEvent> events, ZoneId zone) {
        Map<String, SeriesOverride> overridden = new HashMap<>();
        for (VEvent event : events) {
            try {
                Optional<RecurrenceId<Temporal>> id = event.getProperty(Property.RECURRENCE_ID);
                if (id.isEmpty()) {
                    continue;
                }
                LocalDate day = dayOf(id.get().getDate(), zone);
                if (day == null) {
                    continue;
                }
                SeriesOverride override = overridden.computeIfAbsent(uidOf(event), key -> new SeriesOverride());
                Optional<Range> range = id.get().getParameter(Parameter.RANGE);
                if (range.isPresent() && Range.THISANDFUTURE.equals(range.get())) {
                    override.thisAndFutureFrom(day);
                } else {
                    override.exact.add(day);
                }
            } catch (RuntimeException unreadable) {
                // Un RECURRENCE-ID illisible n'ôte rien : le VEVENT est de toute façon lu seul.
            }
        }
        return overridden;
    }

    /** Occurrences retirées d'une série par des {@code RECURRENCE-ID} du même UID. */
    private static final class SeriesOverride {
        static final SeriesOverride NONE = new SeriesOverride();

        private final Set<LocalDate> exact = new HashSet<>();
        private LocalDate thisAndFutureFrom;

        void thisAndFutureFrom(LocalDate day) {
            if (thisAndFutureFrom == null || day.isBefore(thisAndFutureFrom)) {
                thisAndFutureFrom = day;
            }
        }

        boolean skips(LocalDate day) {
            if (exact.contains(day)) {
                return true;
            }
            return thisAndFutureFrom != null && !day.isBefore(thisAndFutureFrom);
        }
    }

    private static LocalDate dayOf(Temporal temporal, ZoneId zone) {
        if (temporal instanceof LocalDate day) {
            return day;
        }
        if (temporal instanceof ZonedDateTime zoned) {
            return zoned.toLocalDate();
        }
        if (temporal instanceof OffsetDateTime offset) {
            return offset.toLocalDate();
        }
        if (temporal instanceof Instant instant) {
            return instant.atZone(zone).toLocalDate();
        }
        if (temporal instanceof java.time.LocalDateTime local) {
            return local.toLocalDate();
        }
        return null;
    }

    private static String uidOf(VEvent event) {
        return event.getUid().map(Property::getValue).orElse("");
    }

    /**
     * Empreinte de ce que le fichier donne : ses périodes et leurs libellés, triés. Elle ne dépend ni de l'ordre des
     * événements ni des champs que certains agendas en ligne régénèrent à chaque téléchargement ({@code DTSTAMP},
     * {@code PRODID}). Sans quoi la comparaison entre l'aperçu et l'application, qui téléchargent chacun le fichier,
     * échouerait toujours.
     */
    private static String fingerprint(Collection<CalendarEntry> entries) {
        // Forme injective : début|fin|longueur:libellé. La longueur du libellé est déclarée, donc un libellé qui contient
        // « | » ou un saut de ligne ne peut pas se faire passer pour d'autres périodes.
        String canonical = entries.stream()
                .map(entry -> {
                    String label = entry.label() == null ? "" : entry.label();
                    return entry.start() + "|" + entry.end() + "|" + label.length() + ":" + label;
                })
                .sorted()
                .collect(java.util.stream.Collectors.joining("\n"));
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private static String truncate(String label) {
        return label.length() > CalendarEntry.MAX_LABEL ? label.substring(0, CalendarEntry.MAX_LABEL) : label;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
