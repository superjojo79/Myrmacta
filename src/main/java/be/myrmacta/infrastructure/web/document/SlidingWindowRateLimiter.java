package be.myrmacta.infrastructure.web.document;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

final class SlidingWindowRateLimiter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    SlidingWindowRateLimiter(Clock clock) {
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
