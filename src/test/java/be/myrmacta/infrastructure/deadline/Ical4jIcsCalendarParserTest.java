package be.myrmacta.infrastructure.deadline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.deadline.model.CalendarEntry;
import be.myrmacta.domain.deadline.model.CalendarEntry.Nature;
import be.myrmacta.domain.deadline.model.IcsIgnoreReason;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser;
import be.myrmacta.domain.deadline.port.out.IcsCalendarParser.ParsedIcs;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** TODO §121 (D-CAL-ICS) — journées entières, récurrences N-1 à N+2, événements écartés, plafonds, aucun réseau. */
class Ical4jIcsCalendarParserTest {

    private static final ZoneId BRUSSELS = ZoneId.of("Europe/Brussels");
    private final Ical4jIcsCalendarParser parser = new Ical4jIcsCalendarParser();

    private static byte[] ics(String events) {
        return ("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Test//FR\n" + events + "END:VCALENDAR\n")
                .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String event(String uid, String... lines) {
        return "BEGIN:VEVENT\nUID:" + uid + "\nDTSTAMP:20260101T000000Z\n" + String.join("\n", lines) + "\nEND:VEVENT\n";
    }

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static CalendarEntry closed(String start, String end, String label) {
        return new CalendarEntry(d(start), d(end), Nature.CLOSED, label);
    }

    @Test
    void anAllDayEventIsOneClosedDay_withItsSummaryAsLabel() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20261225", "DTEND;VALUE=DATE:20261226", "SUMMARY:Noël")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).containsExactly(closed("2026-12-25", "2026-12-25", "Noël"));
        assertThat(parsed.ignored()).isEmpty();
    }

    @Test
    void aMultiDayEventIsOnePeriod_theEndDateBeingExclusive() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20261224", "DTEND;VALUE=DATE:20270101", "SUMMARY:Congé collectif")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).containsExactly(closed("2026-12-24", "2026-12-31", "Congé collectif"));
    }

    @Test
    void withoutEndOrDurationAnAllDayEventLastsOneDay_andADurationIsHonoured() {
        ParsedIcs parsed = parser.parse(ics(
                event("a", "DTSTART;VALUE=DATE:20261111", "SUMMARY:Armistice")
                        + event("b", "DTSTART;VALUE=DATE:20261201", "DURATION:P3D", "SUMMARY:Fermeture")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).containsExactly(closed("2026-11-11", "2026-11-11", "Armistice"),
                closed("2026-12-01", "2026-12-03", "Fermeture"));
    }

    @Test
    void aYearlyRuleIsUnrolledOverTheWindowOnly_andExdateRemovesAnOccurrence() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20200721", "RRULE:FREQ=YEARLY",
                "EXDATE;VALUE=DATE:20270721", "SUMMARY:Fête nationale")), BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start)
                .containsExactly(d("2025-07-21"), d("2026-07-21"), d("2028-07-21"));
        assertThat(parsed.closedDays()).allSatisfy(entry -> {
            assertThat(entry.dayCount()).isEqualTo(1);
            assertThat(entry.label()).isEqualTo("Fête nationale");
        });
    }

    @Test
    void anRdateAddsAnOccurrence() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20261101", "RDATE;VALUE=DATE:20261111", "SUMMARY:Ponts")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-11-01"), d("2026-11-11"));
    }

    @Test
    void timedCancelledAndTransparentEventsAreIgnoredAndCounted() {
        ParsedIcs parsed = parser.parse(ics(
                event("timed", "DTSTART:20261105T090000Z", "DTEND:20261105T100000Z", "SUMMARY:Réunion")
                        + event("cancelled", "DTSTART;VALUE=DATE:20261106", "STATUS:CANCELLED", "SUMMARY:Annulé")
                        + event("transparent", "DTSTART;VALUE=DATE:20261107", "TRANSP:TRANSPARENT", "SUMMARY:Libre")
                        + event("kept", "DTSTART;VALUE=DATE:20261108", "TRANSP:OPAQUE", "STATUS:CONFIRMED", "SUMMARY:Retenu")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).containsExactly(closed("2026-11-08", "2026-11-08", "Retenu"));
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.TIMED, 1)
                .containsEntry(IcsIgnoreReason.CANCELLED, 1)
                .containsEntry(IcsIgnoreReason.TRANSPARENT, 1)
                .doesNotContainKey(IcsIgnoreReason.INVALID);
    }

    @Test
    void aPeriodOfMoreThan366DaysIsIgnoredAsInvalid_butTheOtherEventsStay() {
        ParsedIcs parsed = parser.parse(ics(
                event("long", "DTSTART;VALUE=DATE:20260101", "DTEND;VALUE=DATE:20280101", "SUMMARY:Trop long")
                        + event("ok", "DTSTART;VALUE=DATE:20261111", "SUMMARY:Armistice")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).containsExactly(closed("2026-11-11", "2026-11-11", "Armistice"));
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.INVALID, 1);
    }

    @Test
    void anEventEndingBeforeItStartsIsInvalid() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20261112", "DTEND;VALUE=DATE:20261110", "SUMMARY:Retour")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).isEmpty();
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.INVALID, 1);
    }

    @Test
    void aRuleFinerThanDailyIsInvalid() {
        ParsedIcs parsed = parser.parse(ics(event("a", "DTSTART;VALUE=DATE:20261111", "RRULE:FREQ=SECONDLY", "SUMMARY:Boucle")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).isEmpty();
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.INVALID, 1);
    }

    @Test
    void theLabelIsTruncatedTo120Characters_andABlankSummaryGivesNoLabel() {
        ParsedIcs parsed = parser.parse(ics(
                event("a", "DTSTART;VALUE=DATE:20261111", "SUMMARY:" + "x".repeat(300))
                        + event("b", "DTSTART;VALUE=DATE:20261112")),
                BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays().get(0).label()).hasSize(120);
        assertThat(parsed.closedDays().get(1).label()).isNull();
    }

    // TODO §121 (revue de #33) — l'empreinte décrit ce que le fichier donne (périodes et libellés), pas ses octets : un
    // agenda en ligne qui régénère DTSTAMP ou PRODID à chaque téléchargement garde la même empreinte, sans quoi la
    // comparaison de l'aperçu et de l'application échouerait toujours.
    @Test
    void theShaIgnoresVolatileFieldsAndEventOrder_butFollowsPeriodsAndLabels() {
        String a = event("a", "DTSTART;VALUE=DATE:20261111", "SUMMARY:Armistice");
        String b = event("b", "DTSTART;VALUE=DATE:20261225", "SUMMARY:Noël");
        String sha = parser.parse(ics(a + b), BRUSSELS, 2025, 2028).sha256();

        byte[] regenerated = ("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Autre horodatage 20261107T101010//FR\nX-GENERATED:" + System.nanoTime() + "\n"
                + (b + a).replace("DTSTAMP:20260101T000000Z", "DTSTAMP:20261107T101010Z") + "END:VCALENDAR\n")
                .replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);

        assertThat(parser.parse(regenerated, BRUSSELS, 2025, 2028).sha256()).isEqualTo(sha);
        assertThat(parser.parse(ics(a + event("b", "DTSTART;VALUE=DATE:20261226", "SUMMARY:Noël")), BRUSSELS, 2025, 2028).sha256())
                .isNotEqualTo(sha);
        assertThat(parser.parse(ics(a + event("b", "DTSTART;VALUE=DATE:20261225", "SUMMARY:Noel")), BRUSSELS, 2025, 2028).sha256())
                .isNotEqualTo(sha);
        assertThat(parser.parse(ics(a), BRUSSELS, 2025, 2028).sha256()).isNotEqualTo(sha);
        assertThat(sha).matches("[0-9a-f]{64}");
    }

    @Test
    void twoFilesWithoutAnyClosedDayShareTheSameSha() {
        assertThat(parser.parse(ics(event("t", "DTSTART:20261105T090000Z", "DTEND:20261105T100000Z")), BRUSSELS, 2025, 2028).sha256())
                .isEqualTo(parser.parse(ics(event("c", "DTSTART;VALUE=DATE:20261106", "STATUS:CANCELLED")), BRUSSELS, 2025, 2028).sha256());
    }

    // TODO §121 (revue de #33) — RECURRENCE-ID : l'occurrence d'une série annulée ou déplacée par un autre VEVENT de même UID.

    private static final String WEEKLY = event("serie", "DTSTART;VALUE=DATE:20260105", "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:Cours");

    @Test
    void aCancelledOverrideRemovesThatOccurrenceOfTheSeries() {
        ParsedIcs parsed = parser.parse(ics(WEEKLY + event("serie", "RECURRENCE-ID;VALUE=DATE:20260112", "DTSTART;VALUE=DATE:20260112",
                "STATUS:CANCELLED", "SUMMARY:Cours")), BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-01-05"), d("2026-01-19"));
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.CANCELLED, 1);
    }

    @Test
    void aMovedOccurrenceClosesTheNewDayAndNotTheOldOne() {
        ParsedIcs parsed = parser.parse(ics(WEEKLY + event("serie", "RECURRENCE-ID;VALUE=DATE:20260112", "DTSTART;VALUE=DATE:20260114",
                "SUMMARY:Cours déplacé")), BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-01-05"), d("2026-01-14"), d("2026-01-19"));
        assertThat(parsed.closedDays().get(1).label()).isEqualTo("Cours déplacé");
    }

    @Test
    void aTransparentOverrideAlsoFreesTheOccurrence() {
        ParsedIcs parsed = parser.parse(ics(WEEKLY + event("serie", "RECURRENCE-ID;VALUE=DATE:20260112", "DTSTART;VALUE=DATE:20260112",
                "TRANSP:TRANSPARENT")), BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-01-05"), d("2026-01-19"));
    }

    @Test
    void anOverrideOfAnotherSeriesOrWithoutAnySeriesDoesNotTouchThisOne() {
        ParsedIcs other = parser.parse(ics(WEEKLY + event("autre", "RECURRENCE-ID;VALUE=DATE:20260112", "DTSTART;VALUE=DATE:20260112",
                "STATUS:CANCELLED")), BRUSSELS, 2025, 2028);
        ParsedIcs orphan = parser.parse(ics(event("seul", "RECURRENCE-ID;VALUE=DATE:20260112", "DTSTART;VALUE=DATE:20260113", "SUMMARY:Seul")),
                BRUSSELS, 2025, 2028);

        assertThat(other.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-01-05"), d("2026-01-12"), d("2026-01-19"));
        assertThat(orphan.closedDays()).containsExactly(closed("2026-01-13", "2026-01-13", "Seul"));
    }

    @Test
    void anOverrideGivenAsADateTimeStillTargetsTheOccurrenceOfThatDay() {
        ParsedIcs parsed = parser.parse(ics(WEEKLY + event("serie", "RECURRENCE-ID:20260112T000000Z", "DTSTART:20260112T090000Z",
                "DTEND:20260112T100000Z")), BRUSSELS, 2025, 2028);

        assertThat(parsed.closedDays()).extracting(CalendarEntry::start).containsExactly(d("2026-01-05"), d("2026-01-19"));
        assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.TIMED, 1);
    }

    @Test
    void aFileWithLineFeedsOnlyIsAccepted() {
        byte[] content = ("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//T//FR\nBEGIN:VEVENT\nUID:a\nDTSTAMP:20260101T000000Z\n"
                + "DTSTART;VALUE=DATE:20261111\nSUMMARY:Armistice\nEND:VEVENT\nEND:VCALENDAR\n").getBytes(StandardCharsets.UTF_8);

        assertThat(parser.parse(content, BRUSSELS, 2025, 2028).closedDays()).hasSize(1);
    }

    @Test
    void anUnreadableOrEmptyFileIsRefused() {
        assertThatThrownBy(() -> parser.parse("pas un calendrier".getBytes(StandardCharsets.UTF_8), BRUSSELS, 2025, 2028))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(new byte[0], BRUSSELS, 2025, 2028)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(null, BRUSSELS, 2025, 2028)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aFileOverOneMebibyteIsRefusedBeforeParsing() {
        byte[] big = new byte[IcsCalendarParser.MAX_BYTES + 1];

        assertThatThrownBy(() -> parser.parse(big, BRUSSELS, 2025, 2028))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1 MiB");
    }

    @Test
    void moreThan5000EventsAreRefused() {
        StringBuilder events = new StringBuilder();
        for (int i = 0; i < IcsCalendarParser.MAX_EVENTS + 1; i++) {
            events.append("BEGIN:VEVENT\nUID:e").append(i).append("\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:20261111\nEND:VEVENT\n");
        }
        byte[] content = ics(events.toString());
        assertThat(content.length).isLessThan(IcsCalendarParser.MAX_BYTES);

        assertThatThrownBy(() -> parser.parse(content, BRUSSELS, 2025, 2028))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5000");
    }

    @Test
    void exactly5000EventsAreAccepted() {
        StringBuilder events = new StringBuilder();
        for (int i = 0; i < IcsCalendarParser.MAX_EVENTS; i++) {
            events.append("BEGIN:VEVENT\nUID:e").append(i).append("\nDTSTAMP:20260101T000000Z\nDTSTART;VALUE=DATE:20261111\nEND:VEVENT\n");
        }

        assertThat(parser.parse(ics(events.toString()), BRUSSELS, 2025, 2028).closedDays()).hasSize(5000);
    }

    @Test
    void dailyRulesThatWouldExceedThePeriodCapAreRefusedInsteadOfFillingTheMemory() {
        StringBuilder events = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            events.append(event("d" + i, "DTSTART;VALUE=DATE:20200101", "RRULE:FREQ=DAILY", "SUMMARY:Chaque jour"));
        }

        assertThatThrownBy(() -> parser.parse(ics(events.toString()), BRUSSELS, 2025, 2028))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10000");
    }

    @Test
    void readingNeverCallsTheNetwork_evenWhenTheFileNamesATimeZoneUrl() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/tz";
            byte[] content = ("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//T//FR\nBEGIN:VTIMEZONE\nTZID:Custom/Zone\nTZURL:" + url
                    + "\nBEGIN:STANDARD\nDTSTART:19700101T000000\nTZOFFSETFROM:+0100\nTZOFFSETTO:+0100\nEND:STANDARD\nEND:VTIMEZONE\n"
                    + event("a", "DTSTART;TZID=Custom/Zone:20261105T090000", "DTEND;TZID=Custom/Zone:20261105T100000")
                    + event("b", "DTSTART;VALUE=DATE:20261111", "SUMMARY:Armistice")
                    + "END:VCALENDAR\n").replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8);

            ParsedIcs parsed = parser.parse(content, BRUSSELS, 2025, 2028);

            assertThat(parsed.closedDays()).hasSize(1);
            assertThat(parsed.ignored()).containsEntry(IcsIgnoreReason.TIMED, 1);
            assertThat(hits).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theOutputIsSortedAndStableAcrossReads() {
        byte[] content = ics(event("z", "DTSTART;VALUE=DATE:20261225", "SUMMARY:Noël")
                + event("a", "DTSTART;VALUE=DATE:20261111", "SUMMARY:Armistice"));

        List<CalendarEntry> first = parser.parse(content, BRUSSELS, 2025, 2028).closedDays();

        assertThat(first).extracting(CalendarEntry::start).containsExactly(d("2026-11-11"), d("2026-12-25"));
        assertThat(parser.parse(content, BRUSSELS, 2025, 2028).closedDays()).isEqualTo(first);
    }
}
