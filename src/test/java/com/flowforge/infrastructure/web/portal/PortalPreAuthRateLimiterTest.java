package com.flowforge.infrastructure.web.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.infrastructure.config.PortalProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PortalPreAuthRateLimiterTest {

    private static final PortalProperties PROPS = new PortalProperties(false, null, null, null, null, null,
            new PortalProperties.RateLimit(2), 0);

    @Test
    void limitIsPerIpAndPerMinute() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:05Z"));
        PortalPreAuthRateLimiter limiter = new PortalPreAuthRateLimiter(PROPS, clock(now));

        limiter.consumeOrThrow("1.1.1.1");
        limiter.consumeOrThrow("1.1.1.1");
        assertThatThrownBy(() -> limiter.consumeOrThrow("1.1.1.1")).isInstanceOf(PortalRateLimitedException.class);
        assertThatCode(() -> limiter.consumeOrThrow("2.2.2.2")).doesNotThrowAnyException();

        now.set(Instant.parse("2026-09-24T10:01:05Z"));
        assertThatCode(() -> limiter.consumeOrThrow("1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void countersFromPastWindowsArePruned_whenTheTableIsFull() {
        // F5 (TODO §54) : une rafale d'IP distinctes ne fait pas croître la table sans limite.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:05Z"));
        PortalPreAuthRateLimiter limiter = new PortalPreAuthRateLimiter(PROPS, clock(now));
        fill(limiter, PortalPreAuthRateLimiter.MAX_TRACKED_CLIENTS);

        now.set(Instant.parse("2026-09-24T10:02:05Z"));
        limiter.consumeOrThrow("9.9.9.9");

        assertThat(limiter.size()).isEqualTo(1);
    }

    @Test
    void distinctClientsWithinOneMinute_neverGrowTheTablePastItsCap() {
        // H1 (revue adversariale) : la purge ne retirait que les minutes passées — des clés
        // distinctes dans la minute courante (IPv6, en-têtes forgés) n'étaient jamais bornées, et
        // chaque requête au-delà du seuil parcourait toute la table.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:05Z"));
        PortalPreAuthRateLimiter limiter = new PortalPreAuthRateLimiter(PROPS, clock(now));
        fill(limiter, PortalPreAuthRateLimiter.MAX_TRACKED_CLIENTS * 2);

        assertThat(limiter.size()).isLessThanOrEqualTo(PortalPreAuthRateLimiter.MAX_TRACKED_CLIENTS + 1);
    }

    @Test
    void onceFull_newClientsShareAnOverflowBucket_andTrackedClientsKeepTheirOwn() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:05Z"));
        PortalPreAuthRateLimiter limiter = new PortalPreAuthRateLimiter(PROPS, clock(now));
        fill(limiter, PortalPreAuthRateLimiter.MAX_TRACKED_CLIENTS);

        limiter.consumeOrThrow("172.31.0.1");
        limiter.consumeOrThrow("172.31.0.2");
        assertThatThrownBy(() -> limiter.consumeOrThrow("172.31.0.3")).isInstanceOf(PortalRateLimitedException.class);
        // Un client déjà suivi (1 requête consommée) garde sa propre fenêtre.
        assertThatCode(() -> limiter.consumeOrThrow("10.0.0.0")).doesNotThrowAnyException();
    }

    @Test
    void ipv6Clients_areBucketedPerSlash64() {
        // Un hôte IPv6 dispose d'ordinaire d'un /64 entier : sans regroupement, chaque adresse
        // d'une même machine aurait sa propre limite.
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-24T10:00:05Z"));
        PortalPreAuthRateLimiter limiter = new PortalPreAuthRateLimiter(PROPS, clock(now));

        limiter.consumeOrThrow("2001:db8:1:2::1");
        limiter.consumeOrThrow("2001:db8:1:2:aaaa:bbbb:cccc:dddd");
        assertThatThrownBy(() -> limiter.consumeOrThrow("2001:db8:1:2::ffff"))
                .isInstanceOf(PortalRateLimitedException.class);
        assertThatCode(() -> limiter.consumeOrThrow("2001:db8:1:3::1")).doesNotThrowAnyException();
    }

    private static void fill(PortalPreAuthRateLimiter limiter, int distinctClients) {
        for (int i = 0; i < distinctClients; i++) {
            try {
                limiter.consumeOrThrow("10." + (i / 62500) + "." + (i / 250 % 250) + "." + (i % 250));
            } catch (PortalRateLimitedException ignored) {
                // au-delà du plafond, les nouveaux clients partagent le seau de débordement
            }
        }
    }

    private static Clock clock(AtomicReference<Instant> now) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
