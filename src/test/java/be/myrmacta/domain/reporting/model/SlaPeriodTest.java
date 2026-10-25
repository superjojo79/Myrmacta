package be.myrmacta.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlaPeriodTest {

    private static final Instant NOW = Instant.parse("2026-03-15T12:00:00Z");

    @Test
    void omittedBounds_defaultToLastThirtyDays() {
        SlaPeriod period = SlaPeriod.resolve(null, null, NOW);

        assertThat(period.from()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(period.to()).isEqualTo(NOW);
    }

    @Test
    void civilDate_fromIsUtcMidnight_toIsNextUtcMidnightExclusive() {
        SlaPeriod period = SlaPeriod.resolve("2026-03-01", "2026-03-31", NOW);

        assertThat(period.from()).isEqualTo(Instant.parse("2026-03-01T00:00:00Z"));
        assertThat(period.to()).isEqualTo(Instant.parse("2026-04-01T00:00:00Z"));
    }

    @Test
    void isoInstant_isParsedAsIs() {
        SlaPeriod period = SlaPeriod.resolve("2026-03-10T00:00:00Z", "2026-03-12T06:00:00Z", NOW);

        assertThat(period.from()).isEqualTo(Instant.parse("2026-03-10T00:00:00Z"));
        assertThat(period.to()).isEqualTo(Instant.parse("2026-03-12T06:00:00Z"));
    }

    @Test
    void invertedOrTooLong_isRejected() {
        assertThatThrownBy(() -> SlaPeriod.resolve("2026-03-10T00:00:00Z", "2026-03-01T00:00:00Z", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("from must be before to");
        assertThatThrownBy(() -> SlaPeriod.resolve("2024-01-01T00:00:00Z", "2026-03-15T00:00:00Z", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("366");
        assertThatThrownBy(() -> SlaPeriod.resolve("not-a-date", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid period");
        assertThatThrownBy(() -> SlaPeriod.resolve("2026-13-40", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid period");
    }

    @Test
    void datetimeLocalWithoutOffset_isRejected() {
        assertThatThrownBy(() -> SlaPeriod.resolve("2026-03-01T00:00", "2026-03-15T23:59", NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid period instant");
    }
}
