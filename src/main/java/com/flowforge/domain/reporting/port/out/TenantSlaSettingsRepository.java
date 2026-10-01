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

    /**
     * Compare-and-set de l'horloge d'export BI (TODO §79, D5) : passe {@code lastBiExportAt} de {@code expected} ({@code null} =
     * jamais exporté) à {@code next} seulement s'il vaut encore {@code expected}. {@code false} quand un autre exécutant a déjà
     * exporté ce créneau.
     */
    boolean advanceLastBiExportAt(String tenantId, Instant expected, Instant next);
}
