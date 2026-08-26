package com.flowforge.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlaClockTest {

    private static final Instant CREATED = Instant.parse("2026-02-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-02-10T00:00:00Z");

    @Test
    void elapsed_usesClosedAtWhenPresent() {
        Instant closed = Instant.parse("2026-02-04T12:00:00Z");

        Duration elapsed = SlaClock.elapsed(CREATED, closed, NOW, NOW, true);

        assertThat(elapsed).isEqualTo(Duration.between(CREATED, closed));
    }

    @Test
    void elapsed_usesNowWhenOpen() {
        Duration elapsed = SlaClock.elapsed(CREATED, null, NOW, NOW, false);

        assertThat(elapsed).isEqualTo(Duration.between(CREATED, NOW));
    }

    @Test
    void elapsed_archiveWithoutClosedAt_usesUpdatedAt() {
        Instant updated = Instant.parse("2026-02-06T00:00:00Z");

        Duration elapsed = SlaClock.elapsed(CREATED, null, updated, NOW, true);

        assertThat(elapsed).isEqualTo(Duration.between(CREATED, updated));
    }

    @Test
    void overdue_whenElapsedStrictlyExceedsTargetHours() {
        Duration exactly48h = Duration.ofHours(48);

        assertThat(SlaClock.overdue(exactly48h, 48)).isFalse();
        assertThat(SlaClock.overdue(exactly48h.plusSeconds(1), 48)).isTrue();
        assertThat(SlaClock.overdue(exactly48h, null)).isFalse();
    }

    @Test
    void remainingHours_canBeNegative() {
        assertThat(SlaClock.remainingHours(Duration.ofHours(50), 48)).isEqualTo(-2.0);
        assertThat(SlaClock.remainingHours(Duration.ofHours(10), 48)).isEqualTo(38.0);
        assertThat(SlaClock.remainingHours(Duration.ofHours(10), null)).isNull();
    }
}
