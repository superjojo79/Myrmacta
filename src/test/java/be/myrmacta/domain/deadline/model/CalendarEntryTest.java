package be.myrmacta.domain.deadline.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** TODO §121 (D-CAL-ENTRY) — une saisie est une période aux bornes incluses. */
class CalendarEntryTest {

    private static final LocalDate DAY = LocalDate.parse("2026-12-24");

    @Test
    void aSingleDayHasEqualBounds_andCoversItself() {
        CalendarEntry entry = CalendarEntry.day(DAY, CalendarEntry.Nature.CLOSED, "Veille de Noël");

        assertThat(entry.start()).isEqualTo(entry.end());
        assertThat(entry.dayCount()).isEqualTo(1);
        assertThat(entry.days()).containsExactly(DAY);
    }

    @Test
    void aPeriodIncludesBothBounds() {
        CalendarEntry entry = new CalendarEntry(DAY, DAY.plusDays(3), CalendarEntry.Nature.CLOSED, "Congé collectif");

        assertThat(entry.dayCount()).isEqualTo(4);
        assertThat(entry.days()).containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2), DAY.plusDays(3));
    }

    @Test
    void endBeforeStart_isRefused() {
        assertThatThrownBy(() -> new CalendarEntry(DAY, DAY.minusDays(1), CalendarEntry.Nature.CLOSED, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPeriodIsCappedAt366Days() {
        assertThat(new CalendarEntry(DAY, DAY.plusDays(365), CalendarEntry.Nature.CLOSED, null).dayCount()).isEqualTo(366);
        assertThatThrownBy(() -> new CalendarEntry(DAY, DAY.plusDays(366), CalendarEntry.Nature.CLOSED, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
    }

    @Test
    void theLabelIsStripped_blankBecomesNull_andIsCappedAt120Characters() {
        assertThat(CalendarEntry.day(DAY, CalendarEntry.Nature.OPEN, "  Fête  ").label()).isEqualTo("Fête");
        assertThat(CalendarEntry.day(DAY, CalendarEntry.Nature.OPEN, "   ").label()).isNull();
        assertThat(CalendarEntry.day(DAY, CalendarEntry.Nature.OPEN, "x".repeat(120)).label()).hasSize(120);
        assertThatThrownBy(() -> CalendarEntry.day(DAY, CalendarEntry.Nature.OPEN, "x".repeat(121)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void boundsAndNatureAreRequired() {
        assertThatThrownBy(() -> new CalendarEntry(null, DAY, CalendarEntry.Nature.CLOSED, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new CalendarEntry(DAY, null, CalendarEntry.Nature.CLOSED, null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new CalendarEntry(DAY, DAY, null, null)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void parsesTheNatureCaseInsensitively_andRefusesAnUnknownOne() {
        assertThat(CalendarEntry.Nature.parse("open")).isEqualTo(CalendarEntry.Nature.OPEN);
        assertThat(CalendarEntry.Nature.parse(" CLOSED ")).isEqualTo(CalendarEntry.Nature.CLOSED);
        assertThatThrownBy(() -> CalendarEntry.Nature.parse("MAYBE")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalendarEntry.Nature.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
