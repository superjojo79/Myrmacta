package com.flowforge.domain.reporting.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class SlaStatusTest {

    @Test
    void withoutTarget_isUnscored() {
        assertThat(SlaStatus.of(Duration.ofHours(10), null, false)).isNull();
    }

    @Test
    void open_belowEightyPercent_isOnTrack() {
        // 10h target: 80% = 8h; 7 truncated hours stays ON_TRACK (7*5=35 < 10*4=40)
        assertThat(SlaStatus.of(Duration.ofHours(7), 10, false)).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    void open_atEightyPercent_isAtRisk() {
        // A-AT-RISK-CMP: elapsedHours * 5 >= slaTargetHours * 4 and not overdue
        assertThat(SlaStatus.of(Duration.ofHours(8), 10, false)).isEqualTo(SlaStatus.AT_RISK);
        assertThat(SlaStatus.of(Duration.ofHours(10), 10, false)).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    void open_pastTarget_isBreached() {
        assertThat(SlaStatus.of(Duration.ofHours(10).plusMillis(1), 10, false)).isEqualTo(SlaStatus.BREACHED);
    }

    @Test
    void closed_neverAtRisk_onTrackOrBreachedOnly() {
        assertThat(SlaStatus.of(Duration.ofHours(8), 10, true)).isEqualTo(SlaStatus.ON_TRACK);
        assertThat(SlaStatus.of(Duration.ofHours(10).plusSeconds(1), 10, true)).isEqualTo(SlaStatus.BREACHED);
    }
}
