package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;

public interface UpsertTenantSlaSettingsUseCase {

    TenantSlaSettings upsert(Command command, AccessContext.User actor);

    record Command(String tenantId, int complianceTargetPercent) {}
}
