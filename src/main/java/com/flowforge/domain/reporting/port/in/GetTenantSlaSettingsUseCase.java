package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;

public interface GetTenantSlaSettingsUseCase {
    TenantSlaSettings get(String tenantId, AccessContext.User actor);
}
