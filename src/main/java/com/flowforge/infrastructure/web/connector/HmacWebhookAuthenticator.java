package com.flowforge.infrastructure.web.connector;

import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * D-CALLBACK-REPLAY (found in adversarial security review) : the signature alone (no timestamp,
 * no binding to anything but the raw body) let an attacker who ever captured one valid
 * (body, signature) pair -- a leaked log line, a compromised intermediary, a debugging tool on
 * the caller's side -- replay it indefinitely with a fresh {@code Idempotency-Key} each time,
 * bypassing idempotency entirely since that header is caller-supplied and unsigned. Signing now
 * covers {@code timestamp + "." + idempotencyKey + "." + body} (Stripe/GitHub-style) with a bounded
 * freshness window : a captured request is only replayable within {@link #TOLERANCE} of its original
 * signing time, not forever — and a fresh {@code Idempotency-Key} cannot reuse a captured signature
 * (TODO §57 M7). This is a breaking change to the wire contract for existing HMAC callers, who must
 * start sending {@value #TIMESTAMP_HEADER} and include the {@code Idempotency-Key} in the HMAC.
 */
@Component
public class HmacWebhookAuthenticator implements WebhookAuthenticator {

    private static final String SIGNATURE_HEADER = "X-Signature-256";
    private static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Duration TOLERANCE = Duration.ofMinutes(5);

    private final Clock clock;

    public HmacWebhookAuthenticator() {
        this(Clock.systemUTC());
    }

    HmacWebhookAuthenticator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.HMAC_SHA256;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        String providedSignature = headers.getFirst(SIGNATURE_HEADER);
        String providedTimestamp = headers.getFirst(TIMESTAMP_HEADER);
        String idempotencyKey = headers.getFirst("Idempotency-Key");
        if (webhookSecret == null
                || webhookSecret.isBlank()
                || providedSignature == null
                || providedSignature.isBlank()
                || providedTimestamp == null
                || providedTimestamp.isBlank()
                || idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new WebhookAuthenticationException(instanceId);
        }
        Instant signedAt = parseTimestamp(providedTimestamp, instanceId);
        Instant now = clock.instant();
        if (signedAt.isBefore(now.minus(TOLERANCE)) || signedAt.isAfter(now.plus(TOLERANCE))) {
            throw new WebhookAuthenticationException(instanceId);
        }
        String expected = PREFIX
                + hmacHex(
                        webhookSecret,
                        signedPayload(
                                providedTimestamp,
                                idempotencyKey,
                                rawBody == null ? new byte[0] : rawBody));
        if (!constantTimeEquals(expected, providedSignature)) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }

    private static Instant parseTimestamp(String providedTimestamp, UUID instanceId) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(providedTimestamp));
        } catch (NumberFormatException ex) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }

    private static byte[] signedPayload(String timestamp, String idempotencyKey, byte[] rawBody) {
        byte[] prefix = (timestamp + "." + idempotencyKey + ".").getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(rawBody, 0, combined, prefix.length, rawBody.length);
        return combined;
    }

    private static String hmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }

    static boolean constantTimeEquals(String expected, String provided) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
