package com.flowforge.domain.dossier.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;

public interface UpsertTenantRetentionSettingsUseCase {

    TenantRetentionSettings upsert(UpsertCommand command, AccessContext.User actor);

    record UpsertCommand(String tenantId, int defaultRetentionDays) {}
}
