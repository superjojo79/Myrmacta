package com.flowforge.domain.reporting.model;

import java.time.Instant;
import java.util.Objects;

public record TenantSlaSettings(
        String tenantId, int complianceTargetPercent, Instant updatedAt, String updatedBy) {

    public static final int DEFAULT_COMPLIANCE_TARGET_PERCENT = 95;

    public TenantSlaSettings {
        Objects.requireNonNull(tenantId, "tenantId");
        if (complianceTargetPercent < 1 || complianceTargetPercent > 100) {
            throw new IllegalArgumentException("complianceTargetPercent must be between 1 and 100");
        }
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(updatedBy, "updatedBy");
    }
}
