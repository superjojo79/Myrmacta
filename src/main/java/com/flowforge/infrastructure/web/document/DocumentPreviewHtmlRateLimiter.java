package com.flowforge.infrastructure.web.document;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Dedicated sliding-window limiter for {@code POST /api/documents/preview} (HTML). Separate
 * instance from {@link DocumentGenerationRateLimiter} so HTML and generate/PDF counters never
 * share a principal key.
 */
@Component
class DocumentPreviewHtmlRateLimiter {

    private final SlidingWindowRateLimiter delegate;

    DocumentPreviewHtmlRateLimiter() {
        this(Clock.systemUTC());
    }

    DocumentPreviewHtmlRateLimiter(Clock clock) {
        this.delegate = new SlidingWindowRateLimiter(clock);
    }

    boolean tryConsume(String principal, int limitPerMinute) {
        return delegate.tryConsume(principal, limitPerMinute);
    }
}
