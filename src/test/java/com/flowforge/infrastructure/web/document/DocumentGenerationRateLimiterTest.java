package com.flowforge.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DocumentGenerationRateLimiterTest {

    @Test
    void allowsRequestsUpToTheLimitThenRejects() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);
        DocumentGenerationRateLimiter limiter = new DocumentGenerationRateLimiter(fixed);

        assertThat(limiter.tryConsume("u1", 2)).isTrue();
        assertThat(limiter.tryConsume("u1", 2)).isTrue();
        assertThat(limiter.tryConsume("u1", 2)).isFalse();
    }

    @Test
    void tracksEachPrincipalIndependently() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-13T10:00:00Z"), ZoneOffset.UTC);
        DocumentGenerationRateLimiter limiter = new DocumentGenerationRateLimiter(fixed);

        assertThat(limiter.tryConsume("u1", 1)).isTrue();
        assertThat(limiter.tryConsume("u1", 1)).isFalse();
        assertThat(limiter.tryConsume("u2", 1)).isTrue();
    }

    @Test
    void resetsWhenTheMinuteWindowRolls() {
        Instant start = Instant.parse("2026-08-13T10:00:00Z");
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(start);
        Clock moving = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        DocumentGenerationRateLimiter limiter = new DocumentGenerationRateLimiter(moving);

        assertThat(limiter.tryConsume("u1", 1)).isTrue();
        assertThat(limiter.tryConsume("u1", 1)).isFalse();

        now.set(start.plusSeconds(61));
        assertThat(limiter.tryConsume("u1", 1)).isTrue();
    }
}
