package com.flowforge.domain.notification.model;

import java.util.Map;
import java.util.Objects;

/**
 * D-WEBHOOK-MASK (masquage-donnees-sensibles-logs-webhooks): optional context a
 * {@link NotificationEvent} producer attaches when its payload should carry dossier field
 * values. {@code SendNotificationUseCaseImpl} resolves {@code dossierTypeCode}'s real
 * {@code FieldDefinition}s and strips sensitive values before any outbox write — the producer
 * only supplies the raw snapshot, it does not need to know which fields are sensitive itself.
 */
public record DossierFieldsSnapshot(String dossierTypeCode, Map<String, Object> fields) {
    public DossierFieldsSnapshot {
        Objects.requireNonNull(dossierTypeCode, "dossierTypeCode");
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
