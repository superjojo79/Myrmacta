package be.myrmacta.infrastructure.web.connector;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

@Component
class WebhookRequestRateLimiter {

    private final ConcurrentHashMap<UUID, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    WebhookRequestRateLimiter() {
        this(Clock.systemUTC());
    }

    WebhookRequestRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean tryConsume(UUID instanceId, int limitPerMinute) {
        long window = clock.instant().getEpochSecond() / 60;
        AtomicBoolean allowed = new AtomicBoolean(true);
        counters.compute(instanceId, (id, current) -> {
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
