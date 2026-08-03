package com.flowforge.infrastructure.web.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowforge.domain.connector.model.WebhookAuthType;
import com.flowforge.domain.connector.model.WebhookAuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class WebhookAuthenticatorTest {

    private static final UUID INSTANCE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void hmacAuthenticator_acceptsValidSha256Signature() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature-256", "sha256=" + hmacHex("webhook-secret", body));

        var authenticator = new HmacWebhookAuthenticator();

        assertThat(authenticator.supports(WebhookAuthType.HMAC_SHA256)).isTrue();
        authenticator.authenticate(INSTANCE_ID, "webhook-secret", body, headers);
    }

    @Test
    void hmacAuthenticator_rejectsInvalidSignature() {
        byte[] body = "{\"event\":\"created\"}".getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Signature-256", "sha256=invalid");

        assertThatThrownBy(() -> new HmacWebhookAuthenticator()
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
    void sharedSecretAuthenticator_rejectsWrongToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Webhook-Token", "wrong");

        assertThatThrownBy(() -> new SharedSecretWebhookAuthenticator()
                        .authenticate(INSTANCE_ID, "webhook-secret", new byte[0], headers))
                .isInstanceOf(WebhookAuthenticationException.class);
    }

    private static String hmacHex(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
