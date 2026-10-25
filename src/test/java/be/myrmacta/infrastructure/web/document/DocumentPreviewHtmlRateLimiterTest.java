package be.myrmacta.infrastructure.web.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DocumentPreviewHtmlRateLimiterTest {

    @Test
    void allowsRequestsUpToTheLimitThenRejects() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneOffset.UTC);
        DocumentPreviewHtmlRateLimiter limiter = new DocumentPreviewHtmlRateLimiter(fixed);

        assertThat(limiter.tryConsume("u1", 2)).isTrue();
        assertThat(limiter.tryConsume("u1", 2)).isTrue();
        assertThat(limiter.tryConsume("u1", 2)).isFalse();
    }

    @Test
    void doesNotShareCountersWithGenerateLimiter() {
        Clock fixed = Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneOffset.UTC);
        DocumentPreviewHtmlRateLimiter html = new DocumentPreviewHtmlRateLimiter(fixed);
        DocumentGenerationRateLimiter generate = new DocumentGenerationRateLimiter(fixed);

        assertThat(html.tryConsume("u1", 1)).isTrue();
        assertThat(html.tryConsume("u1", 1)).isFalse();
        assertThat(generate.tryConsume("u1", 1)).isTrue();
    }
}
