package com.flowforge.infrastructure.web.document;

import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Sliding one-minute-window rate limiter for {@code POST /api/documents/generate} and
 * {@code POST /api/documents/preview/pdf}, keyed by the authenticated caller.
 */
@Component
class DocumentGenerationRateLimiter {

    private final SlidingWindowRateLimiter delegate;

    DocumentGenerationRateLimiter() {
        this(Clock.systemUTC());
    }

    DocumentGenerationRateLimiter(Clock clock) {
        this.delegate = new SlidingWindowRateLimiter(clock);
    }

    boolean tryConsume(String principal, int limitPerMinute) {
        return delegate.tryConsume(principal, limitPerMinute);
    }
}
