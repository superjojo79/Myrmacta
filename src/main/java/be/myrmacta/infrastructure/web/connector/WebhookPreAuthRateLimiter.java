package be.myrmacta.infrastructure.web.connector;

import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Coarse per-instance ceiling applied <em>before</em> webhook authentication (TODO §57 revue
 * adversariale PR #113). A separate counter from {@link WebhookRequestRateLimiter} — sharing one
 * counter between a pre-auth and a post-auth check would let unauthenticated traffic consume the
 * same budget authenticated traffic needs, reintroducing the exact lockout PR #113 (Q10) removed
 * by moving the strict limiter after {@code authenticate()}. This one only bounds unauthenticated
 * abuse (HMAC/parsing cost, bandwidth) and is deliberately configured well above the legitimate
 * limit (see {@link be.myrmacta.infrastructure.config.ConnectorProperties.Webhook}).
 */
@Component
class WebhookPreAuthRateLimiter {

    private final ConcurrentHashMap<UUID, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    WebhookPreAuthRateLimiter() {
        this(Clock.systemUTC());
    }

    WebhookPreAuthRateLimiter(Clock clock) {
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
