package com.flowforge.domain.notification.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbound webhook configuration for a tenant. {@code secretCiphertext} may hold
 * plaintext temporarily at create/update time; the persistence adapter encrypts
 * values that are not already ciphertext.
 */
public record NotificationWebhookConfig(
        UUID id,
        String tenantId,
        String name,
        String targetUrl,
        String secretCiphertext,
        boolean enabled,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static final String SECRET_MASK = "********";

    public NotificationWebhookConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(targetUrl, "targetUrl");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Returns a copy with the secret masked for API responses, or {@code null} if absent. */
    public NotificationWebhookConfig withMaskedSecret() {
        return new NotificationWebhookConfig(
                id,
                tenantId,
                name,
                targetUrl,
                secretCiphertext == null ? null : SECRET_MASK,
                enabled,
                deletedAt,
                createdAt,
                updatedAt);
    }
}
