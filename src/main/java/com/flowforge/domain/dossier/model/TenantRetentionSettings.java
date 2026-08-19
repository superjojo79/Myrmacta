package com.flowforge.domain.dossier.model;

import java.time.Instant;
import java.util.Objects;

public record TenantRetentionSettings(
        String tenantId, int defaultRetentionDays, Instant updatedAt, String updatedBy) {

    public TenantRetentionSettings {
        Objects.requireNonNull(tenantId, "tenantId");
        if (defaultRetentionDays <= 0) {
            throw new IllegalArgumentException("defaultRetentionDays must be > 0");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}
