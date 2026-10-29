package be.myrmacta.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WebhookAuthenticatorTest {

    private static final UUID INSTANCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant FIXED_NOW = Instant.parse("2026-09-03T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    @Test
    void hmacAuthenticator_acceptsValidSignatureWithFreshTimestamp() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", timestamp);
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", timestamp, "idem-1", body));

        var authenticator = new HmacWebhookAuthenticator(FIXED_CLOCK);

        assertThat(authenticator.supports(WebhookAuthType.HMAC_SHA256)).isTrue();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers);
    }

    @Test
    void hmacAuthenticator_rejectsInvalidSignature() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", String.valueOf(FIXED_NOW.getEpochSecond()));
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=invalid");

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_missingTimestamp_rejected() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", "0", "idem-1", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_nonNumericTimestamp_rejected() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", "not-a-number");
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", "not-a-number", "idem-1", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_staleTimestamp_rejected() {
        // D-CALLBACK-REPLAY : a captured (body, signature, timestamp) triple from 10 minutes ago
        // must not still authenticate -- otherwise the timestamp buys nothing over the old
        // body-only signature.
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        String staleTimestamp = String.valueOf(FIXED_NOW.minus(Duration.ofMinutes(10)).getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", staleTimestamp);
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", staleTimestamp, "idem-1", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_futureTimestampBeyondTolerance_rejected() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        String futureTimestamp = String.valueOf(FIXED_NOW.plus(Duration.ofMinutes(10)).getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", futureTimestamp);
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", futureTimestamp, "idem-1", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_signatureComputedWithoutTimestampPrefix_rejected() {
        // Proves the timestamp is actually part of the signed payload, not just an accompanying
        // header -- a captured old-scheme (body-only) signature must not still validate.
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", String.valueOf(FIXED_NOW.getEpochSecond()));
        headers.add("Idempotency-Key", "idem-1");
        headers.add("X-Signature-256", "sha256=" + bodyOnlyHmacHex("webhook-secret", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void sharedSecretAuthenticator_acceptsMatchingToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Token", "webhook-secret");

        var authenticator = new SharedSecretWebhookAuthenticator();

        assertThat(authenticator.supports(WebhookAuthType.SHARED_SECRET_TOKEN)).isTrue();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", new byte[0], headers);
    }

    @Test
    void hmacAuthenticator_signatureOmittingIdempotencyKey_rejected() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", timestamp);
        headers.add("Idempotency-Key", "idem-fresh");
        headers.add("X-Signature-256", "sha256=" + timestampBodyOnlyHmac("webhook-secret", timestamp, body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void hmacAuthenticator_missingIdempotencyKey_rejected() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        String timestamp = String.valueOf(FIXED_NOW.getEpochSecond());
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Timestamp", timestamp);
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", timestamp, "idem-1", body));

        assertThatThrownBy(() -> new HmacWebhookAuthenticator(FIXED_CLOCK)
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void sharedSecretAuthenticator_rejectsReplayOfSameBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Token", "webhook-secret");
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        var authenticator = new SharedSecretWebhookAuthenticator();

        authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers);
        assertThatThrownBy(() -> authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void sharedSecretAuthenticator_acceptsDifferentBodyAfterFirst() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Token", "webhook-secret");
        var authenticator = new SharedSecretWebhookAuthenticator();

        authenticator.authenticate(
                INSTANCE_ID, "webhook-secret", "{\"n\":1}".getBytes(StandardCharsets.UTF_8), headers);
        authenticator.authenticate(
                INSTANCE_ID, "webhook-secret", "{\"n\":2}".getBytes(StandardCharsets.UTF_8), headers);
    }

    @Test
    void yousignAuthenticator_acceptsBodyHmacAndReadsEventId() {
        String eventId = "b6c63685-c556-4a30-8fe9-b6f2b187d936";
        byte[] body = ("{\"event_id\":\"" + eventId + "\",\"event_name\":\"signature_request.done\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Yousign-Signature-256", "sha256=" + bodyOnlyHmacHex("webhook-secret", body));
        headers.add("Idempotency-Key", "forged-key");

        var authenticator = new YousignWebhookAuthenticator();

        assertThat(authenticator.supports(WebhookAuthType.YOUSIGN_HMAC)).isTrue();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers);
        assertThat(authenticator.idempotencyKey(body)).isEqualTo(eventId);
    }

    @Test
    void yousignAuthenticator_rejectsSignatureOfADifferentBody() {
        byte[] body = "{\"event_id\":\"b6c63685-c556-4a30-8fe9-b6f2b187d936\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Yousign-Signature-256", "sha256=" + bodyOnlyHmacHex("webhook-secret", "{\"event_id\":\"other\"}".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> new YousignWebhookAuthenticator()
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void docusignAuthenticator_acceptsAnyMatchingSignatureAndIgnoresRetryCount() {
        String envelope = "0ef334d2-7f36-4643-8a2a-4283b4a3e4a4";
        byte[] first = ("{\"event\":\"envelope-completed\",\"retryCount\":0,\"data\":{\"envelopeId\":\"" + envelope + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] retry = ("{\"event\":\"envelope-completed\",\"retryCount\":2,\"data\":{\"envelopeId\":\"" + envelope + "\"}}")
                .getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-DocuSign-Signature-1", java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        headers.add("X-DocuSign-Signature-2", java.util.Base64.getEncoder().encodeToString(hmacBytes("webhook-secret", first)));
        var authenticator = new DocusignWebhookAuthenticator();

        assertThat(authenticator.supports(WebhookAuthType.DOCUSIGN_HMAC)).isTrue();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", first, headers);
        assertThat(authenticator.idempotencyKey(first)).isEqualTo(envelope + ":envelope-completed");
        assertThat(authenticator.idempotencyKey(retry)).isEqualTo(authenticator.idempotencyKey(first));
    }

    @Test
    void docusignAuthenticator_rejectsASignatureOfAnotherBody() {
        byte[] body = "{\"event\":\"envelope-sent\",\"data\":{\"envelopeId\":\"0ef334d2-7f36-4643-8a2a-4283b4a3e4a4\"}}"
                .getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-DocuSign-Signature-1", java.util.Base64.getEncoder().encodeToString(
                hmacBytes("webhook-secret", "{\"event\":\"other\"}".getBytes(StandardCharsets.UTF_8))));

        assertThatThrownBy(() -> new DocusignWebhookAuthenticator()
                        .authenticate(INSTANCE_ID, "webhook-secret", body, headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    @Test
    void yousignAuthenticator_rejectsBodyWithoutEventId() {
        byte[] body = "{\"event_name\":\"signature_request.done\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Yousign-Signature-256", "sha256=" + bodyOnlyHmacHex("webhook-secret", body));
        var authenticator = new YousignWebhookAuthenticator();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers);

        assertThatThrownBy(() -> authenticator.idempotencyKey(body))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharedSecretAuthenticator_rejectsWrongToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Token", "wrong");

        assertThatThrownBy(() -> new SharedSecretWebhookAuthenticator()
                        .authenticate(INSTANCE_ID, "webhook-secret", new byte[0], headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    private static String hmacHex(String secret, String timestamp, String idempotencyKey, byte[] body) {
        byte[] prefix = (timestamp + "." + idempotencyKey + ".").getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(body, 0, combined, prefix.length, body.length);
        return bodyOnlyHmacHex(secret, combined);
    }

    private static String timestampBodyOnlyHmac(String secret, String timestamp, byte[] body) {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, combined, 0, prefix.length);
        System.arraycopy(body, 0, combined, prefix.length, body.length);
        return bodyOnlyHmacHex(secret, combined);
    }

    private static byte[] hmacBytes(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(body);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String bodyOnlyHmacHex(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
