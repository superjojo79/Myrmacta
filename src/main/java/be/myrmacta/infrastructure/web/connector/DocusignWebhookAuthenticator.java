package be.myrmacta.infrastructure.web.connector;

import be.myrmacta.domain.connector.model.WebhookAuthType;
import be.myrmacta.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §109 (D-SIGNATURE-WEBHOOK) — DocuSign signe le corps brut en Base64
 * ({@code X-DocuSign-Signature-N}, un en-tête par clé HMAC du compte). La clé d'idempotence est
 * {@code envelopeId} + événement, dans le corps déjà authentifié. {@code retryCount} n'en fait pas
 * partie : un rejeu augmente ce compteur.
 */
@Component
public class DocusignWebhookAuthenticator implements WebhookAuthenticator, BodyIdempotency {

    static final String SIGNATURE_HEADER_PREFIX = "X-DocuSign-Signature-";
    private static final int MAX_SIGNATURE_HEADERS = 100;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Override
    public boolean supports(WebhookAuthType authType) {
        return authType == WebhookAuthType.DOCUSIGN_HMAC;
    }

    @Override
    public void authenticate(UUID instanceId, String webhookSecret, byte[] rawBody, HttpHeaders headers) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new WebhookAuthenticationException(instanceId);
        }
        String expected = Base64.getEncoder().encodeToString(hmac(webhookSecret, rawBody == null ? new byte[0] : rawBody));
        boolean match = false;
        for (int index = 1; index <= MAX_SIGNATURE_HEADERS; index++) {
            String provided = headers.getFirst(SIGNATURE_HEADER_PREFIX + index);
            if (provided != null && HmacWebhookAuthenticator.constantTimeEquals(expected, provided.trim())) {
                match = true;
            }
        }
        if (!match) {
            throw new WebhookAuthenticationException(instanceId);
        }
    }

    @Override
    public String idempotencyKey(byte[] rawBody) {
        JsonNode node;
        try {
            node = JSON.readTree(rawBody == null ? new byte[0] : rawBody);
        } catch (JacksonException ex) {
            throw new IllegalArgumentException("DocuSign callback body must be JSON");
        }
        JsonNode event = node == null ? null : node.get("event");
        JsonNode envelope = node == null ? null : node.path("data").get("envelopeId");
        if (event == null || !event.isString() || !event.asString().matches("[a-z0-9-]{1,80}")) {
            throw new IllegalArgumentException("DocuSign callback event is required");
        }
        if (envelope == null || !envelope.isString()) {
            throw new IllegalArgumentException("DocuSign callback envelopeId is required");
        }
        try {
            return UUID.fromString(envelope.asString()) + ":" + event.asString();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("DocuSign callback envelopeId must be a UUID");
        }
    }

    private static byte[] hmac(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA256 calculation failed", ex);
        }
    }
}
