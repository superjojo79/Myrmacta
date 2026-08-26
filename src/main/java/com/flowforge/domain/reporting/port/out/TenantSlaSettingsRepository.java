package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.TenantSlaSettings;
import java.util.Optional;

public interface TenantSlaSettingsRepository {

    Optional<TenantSlaSettings> findByTenantId(String tenantId);

    TenantSlaSettings save(TenantSlaSettings settings);
}
