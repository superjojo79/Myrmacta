package com.flowforge.infrastructure.web.document;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Sliding one-minute-window rate limiter for {@code POST /api/documents/generate}, keyed by the
 * authenticated caller — the least-privileged role allowed on this endpoint (DOSSIER_MANAGER)
 * can otherwise submit large/nested variable maps in a burst and drive up CPU/memory on the
 * Flying Saucer render. Mirrors {@code WebhookRequestRateLimiter}'s in-memory window strategy.
 */
@Component
class DocumentGenerationRateLimiter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    DocumentGenerationRateLimiter() {
        this(Clock.systemUTC());
    }

    DocumentGenerationRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean tryConsume(String principal, int limitPerMinute) {
        long window = clock.instant().getEpochSecond() / 60;
        AtomicBoolean allowed = new AtomicBoolean(true);
        counters.compute(principal, (id, current) -> {
            if (current == null || current.window() != window) {
                return new WindowCounter(window, 1);
            }
            if (current.count() >= limitPerMinute) {
                allowed.set(false);
                return current;
            }
            return new WindowCounter(window, current.count() + 1);
        });
        return allowed.get();
    }

    private record WindowCounter(long window, int count) {}
}
