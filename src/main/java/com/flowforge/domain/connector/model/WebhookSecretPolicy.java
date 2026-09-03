package com.flowforge.domain.connector.model;

/**
 * Minimum length enforced on a webhook/callback shared secret (D-CALLBACK-BRUTEFORCE, found in
 * adversarial security review) : {@code /api/webhooks/**} and {@code /api/callbacks/**} are
 * anonymous and internet-facing by design, with no other layer of defense in front of them (no
 * IP allowlist in V1) -- a short secret is brute-forceable within the per-instance rate limit.
 */
public final class WebhookSecretPolicy {

    public static final int MIN_LENGTH = 20;

    private WebhookSecretPolicy() {
    }

    public static void validate(String webhookSecret) {
        if (webhookSecret.length() < MIN_LENGTH) {
            throw new ConnectorValidationException(
                    "webhookSecret must be at least " + MIN_LENGTH + " characters");
        }
    }
}
