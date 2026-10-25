package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Shared-secret webhook auth (TODO §57 M7). The token itself has no freshness; replay of the same
 * body for the same instance within {@link #REPLAY_TTL} is rejected even with a new
 * {@code Idempotency-Key}. A stolen token with a <em>new</em> body is credential theft, out of
 * scope.
 */
@Component
public class SharedSecretWebhookAuthenticator implements WebhookAuthenticator {

    private static final String HEADER = "X-Webhook-Token";
    private static final Duration REPLAY_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> seenBodies = new ConcurrentHashMap<>();

    public SharedSecretWebhookAuthenticator() {
        this(Clock.systemUTC());
    }

    SharedSecretWebhookAuthenticator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.SHARED_SECRET_TOKEN;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        String provided = headers.getFirst(HEADER);
        if (webhookSecret == null
                || webhookSecret.isBlank()
                || provided == null
                || !HmacWebhookAuthenticator.constantTimeEquals(webhookSecret, provided)) {
            throw new WebhookAuthenticationException(instanceId);
        }
        assertNotReplay(instanceId, rawBody == null ? new byte[0] : rawBody);
    }

    private void assertNotReplay(UUID instanceId, byte[] rawBody) {
        Instant now = clock.instant();
        pruneExpired(now);
        String key = instanceId + ":" + sha256Hex(rawBody);
        Instant expiry = seenBodies.get(key);
        if (expiry != null && expiry.isAfter(now)) {
            throw new WebhookAuthenticationException(instanceId);
        }
        seenBodies.put(key, now.plus(REPLAY_TTL));
    }

    private void pruneExpired(Instant now) {
        Iterator<Map.Entry<String, Instant>> iterator = seenBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Instant> entry = iterator.next();
            if (!entry.getValue().isAfter(now)) {
                iterator.remove();
            }
        }
    }

    private static String sha256Hex(byte[] rawBody) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawBody));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
