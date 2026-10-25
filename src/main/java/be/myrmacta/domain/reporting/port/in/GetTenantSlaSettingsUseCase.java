package be.myrmacta.domain.reporting.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;

public interface GetTenantSlaSettingsUseCase {
    TenantSlaSettings get(String tenantId, AccessContext.User actor);
}
