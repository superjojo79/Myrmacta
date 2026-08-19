package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;

public interface GetTenantRetentionSettingsUseCase {

    TenantRetentionSettings get(String tenantId, AccessContext.User actor);
}
