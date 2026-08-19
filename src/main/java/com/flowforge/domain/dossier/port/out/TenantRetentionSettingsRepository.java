package com.flowforge.domain.dossier.port.out;

import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import java.util.Optional;

public interface TenantRetentionSettingsRepository {

    Optional<TenantRetentionSettings> findByTenantId(String tenantId);

    TenantRetentionSettings save(TenantRetentionSettings settings);
}
