package com.flowforge.infrastructure.web.portal;

import com.flowforge.infrastructure.config.PortalProperties;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * TODO §68 — plafond par client appliqué <em>avant</em> toute recherche de jeton en base
 * (T-ACCESS-4), sur une route anonyme exposée à internet. JVM-local (mono-réplique, S-7 assumé).
 * La clé vient de {@code ClientIpResolver} (C2 : jamais un en-tête contrôlé par le client).
 *
 * <p>Mémoire <strong>bornée</strong> (H1, revue adversariale) : la table ne dépasse pas
 * {@link #MAX_TRACKED_CLIENTS} entrées (à la concurrence près). Quand elle est pleine, les fenêtres
 * passées sont purgées — au plus une fois par minute, pas à chaque requête — puis, si elle l'est
 * encore, les <em>nouveaux</em> clients partagent un seau de débordement unique soumis à la même
 * limite : sous une rafale de clés distinctes, les clients déjà suivis gardent leur quota et seuls
 * les nouveaux venus sont freinés. Les adresses IPv6 sont regroupées par /64 (un hôte en dispose
 * d'ordinaire d'un entier).
 */
@Component
class PortalPreAuthRateLimiter {

    static final int MAX_TRACKED_CLIENTS = 10_000;
    static final String OVERFLOW_KEY = "overflow";

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();
    private final AtomicLong lastPrunedWindow = new AtomicLong(Long.MIN_VALUE);
    private final Clock clock;
    private final PortalProperties properties;

    @Autowired
    PortalPreAuthRateLimiter(PortalProperties properties) {
        this(properties, Clock.systemUTC());
    }

    PortalPreAuthRateLimiter(PortalProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    void consumeOrThrow(String clientAddress) {
        long window = clock.instant().getEpochSecond() / 60;
        String key = bucket(clientAddress);
        if (!counters.containsKey(key) && counters.size() >= MAX_TRACKED_CLIENTS) {
            long pruned = lastPrunedWindow.get();
            if (pruned != window && lastPrunedWindow.compareAndSet(pruned, window)) {
                counters.entrySet().removeIf(entry -> entry.getValue().window() != window);
            }
            if (counters.size() >= MAX_TRACKED_CLIENTS) {
                key = OVERFLOW_KEY;
            }
        }
        int limit = properties.rateLimit().perIpPerMinute();
        AtomicBoolean allowed = new AtomicBoolean(true);
        counters.compute(key, (id, current) -> {
            if (current == null || current.window() != window) {
                return new WindowCounter(window, 1);
            }
            if (current.count() >= limit) {
                allowed.set(false);
                return current;
            }
            return new WindowCounter(window, current.count() + 1);
        });
        if (!allowed.get()) {
            throw new PortalRateLimitedException();
        }
    }

    /** IPv6 → préfixe /64 ; IPv4 et valeurs non IPv6 inchangées. */
    static String bucket(String clientAddress) {
        if (clientAddress == null || clientAddress.isBlank()) {
            return "unknown";
        }
        if (!clientAddress.contains(":")) {
            return clientAddress;
        }
        try {
            byte[] bytes = InetAddress.getByName(clientAddress).getAddress();
            if (bytes.length != 16) {
                return clientAddress;
            }
            return "v6:" + HexFormat.of().formatHex(bytes, 0, 8);
        } catch (UnknownHostException ex) {
            return clientAddress;
        }
    }

    int size() {
        return counters.size();
    }

    private record WindowCounter(long window, int count) {}
}
