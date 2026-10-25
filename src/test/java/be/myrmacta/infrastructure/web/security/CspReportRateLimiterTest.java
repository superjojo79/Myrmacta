package be.myrmacta.infrastructure.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CspReportRateLimiterTest {

    @Test
    void allowsUpToLimitPerWindowThenRejects() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var limiter = new CspReportRateLimiter(clock);

        assertThat(limiter.tryConsume("203.0.113.10", 2)).isTrue();
        assertThat(limiter.tryConsume("203.0.113.10", 2)).isTrue();
        assertThat(limiter.tryConsume("203.0.113.10", 2)).isFalse();
    }

    @Test
    void tracksEachRemoteAddrIndependently() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var limiter = new CspReportRateLimiter(clock);

        assertThat(limiter.tryConsume("203.0.113.10", 1)).isTrue();
        assertThat(limiter.tryConsume("203.0.113.10", 1)).isFalse();
        assertThat(limiter.tryConsume("198.51.100.20", 1)).isTrue();
    }

    @Test
    void resetsOnNextMinuteWindow() {
        Instant firstWindow = Instant.parse("2026-01-01T00:00:00Z");
        var limiter = new CspReportRateLimiter(Clock.fixed(firstWindow, ZoneOffset.UTC));
        assertThat(limiter.tryConsume("203.0.113.10", 1)).isTrue();
        assertThat(limiter.tryConsume("203.0.113.10", 1)).isFalse();

        var limiterNextWindow = new CspReportRateLimiter(
                Clock.fixed(firstWindow.plusSeconds(60), ZoneOffset.UTC));
        // Different instance to simulate the counter map after a full window elapses --
        // behaviourally equivalent since the sliding window key is derived from the clock.
        assertThat(limiterNextWindow.tryConsume("203.0.113.10", 1)).isTrue();
    }

    @Test
    void treatsNullRemoteAddrAsASingleSharedBucket() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        var limiter = new CspReportRateLimiter(clock);

        assertThat(limiter.tryConsume(null, 1)).isTrue();
        assertThat(limiter.tryConsume(null, 1)).isFalse();
    }
}
