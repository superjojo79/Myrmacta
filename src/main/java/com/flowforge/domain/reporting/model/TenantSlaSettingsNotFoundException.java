package com.flowforge.domain.reporting.model;

public class TenantSlaSettingsNotFoundException extends RuntimeException {
    public TenantSlaSettingsNotFoundException(String tenantId) {
        super("SLA settings not found for tenant: " + tenantId);
    }
}
