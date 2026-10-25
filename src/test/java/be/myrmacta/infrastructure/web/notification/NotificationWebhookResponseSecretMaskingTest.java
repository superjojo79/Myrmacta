package be.myrmacta.infrastructure.web.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import be.myrmacta.domain.notification.model.NotificationWebhookConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * D-SECRETS-AUDIT garde-fou : ce test doit rester rouge si une future modification de
 * {@link NotificationWebhookResponse} réintroduit une fuite de secret en clair dans la réponse
 * admin webhooks de notification. {@code secretCiphertext} peut transitoirement porter du
 * plaintext au moment de la création (avant chiffrement par la persistence) — c'est justement le
 * pire cas que ce garde-fou couvre.
 */
class NotificationWebhookResponseSecretMaskingTest {

    private static final String KNOWN_SECRET = "s3cr3t-webhook-value-should-never-leak";

    @Test
    void from_neverExposesSecretValueInSerializedResponse() throws Exception {
        Instant now = Instant.parse("2026-08-21T10:00:00Z");
        NotificationWebhookConfig config = new NotificationWebhookConfig(
                UUID.randomUUID(),
                "tenant_dev",
                "webhook-1",
                "https://example.com/hook",
                KNOWN_SECRET,
                true,
                null,
                now,
                now);

        NotificationWebhookResponse response = NotificationWebhookResponse.from(config);
        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(response);

        assertThat(json).doesNotContain(KNOWN_SECRET);
        assertThat(response.secretConfigured()).isTrue();
    }
}
