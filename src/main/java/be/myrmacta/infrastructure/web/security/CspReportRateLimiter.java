package be.myrmacta.infrastructure.web.security;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/** Plafonne {@code /api/security/csp-reports} par pair TCP -- endpoint anonyme, internet-facing. */
@Component
class CspReportRateLimiter {

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final Clock clock;

    CspReportRateLimiter() {
        this(Clock.systemUTC());
    }

    CspReportRateLimiter(Clock clock) {
        this.clock = clock;
    }

    boolean tryConsume(String remoteAddr, int limitPerMinute) {
        String key = remoteAddr == null ? "unknown" : remoteAddr;
        long window = clock.instant().getEpochSecond() / 60;
        AtomicBoolean allowed = new AtomicBoolean(true);
        counters.compute(key, (id, current) -> {
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
