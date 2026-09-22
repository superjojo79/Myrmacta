package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.TenantSlaSettings;
import java.time.Instant;
import java.util.Optional;

public interface TenantSlaSettingsRepository {

    Optional<TenantSlaSettings> findByTenantId(String tenantId);

    TenantSlaSettings save(TenantSlaSettings settings);

    /**
     * Advances the scan clock only. Must not rewrite recipients, toggles, or the BI clock.
     */
    void advanceLastAlertScanAt(String tenantId, Instant scannedAt);
}
