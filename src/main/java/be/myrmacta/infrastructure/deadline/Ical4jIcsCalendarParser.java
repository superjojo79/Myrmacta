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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.transform.recurrence.Frequency;

/**
 * TODO §121 (D-CAL-ICS) — lecture des calendriers ICS avec ical4j 4.x. Seules les journées entières ({@code VALUE=DATE})
 * deviennent des jours fermés, {@code DTEND} étant exclusif. Aucun appel réseau (voir {@code ical4j.properties}) ; la
 * taille du fichier, le nombre d'événements et le nombre de périodes produites sont bornés.
 */
@org.springframework.stereotype.Component
class Ical4jIcsCalendarParser implements IcsCalendarParser {

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
        Period<LocalDate> window = new Period<>(LocalDate.of(fromYear, 1, 1), LocalDate.of(toYear + 1, 1, 1));
        Map<IcsIgnoreReason, Integer> ignored = new EnumMap<>(IcsIgnoreReason.class);
        List<CalendarEntry> entries = new ArrayList<>();
        for (VEvent event : events) {
            try {
                IcsIgnoreReason reason = unfold(event, window, entries);
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
        return new ParsedIcs(entries, ignored, sha256(content));
    }

    private static Calendar read(byte[] content) {
        try {
            return new CalendarBuilder().build(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));
        } catch (Exception | StackOverflowError unreadable) {
            throw new IllegalArgumentException("calendar file is not a readable iCalendar file");
        }
    }

    /** Ajoute les périodes de l'événement ; rend la raison pour laquelle il est écarté, nul s'il est retenu. */
    private static IcsIgnoreReason unfold(VEvent event, Period<LocalDate> window, Collection<CalendarEntry> into) {
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
            into.add(new CalendarEntry(first, endExclusive.minusDays(1), CalendarEntry.Nature.CLOSED, label));
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
            LocalDate occurrenceEnd = occurrence.getEnd() == null || !occurrence.getEnd().isAfter(occurrence.getStart())
                    ? occurrence.getStart().plusDays(1) : occurrence.getEnd();
            into.add(new CalendarEntry(occurrence.getStart(), occurrenceEnd.minusDays(1), CalendarEntry.Nature.CLOSED, label));
            if (into.size() > CalendarSource.MAX_ENTRIES) {
                return null;
            }
        }
        return null;
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
