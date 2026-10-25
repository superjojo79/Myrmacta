package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.TenantRetentionSettings;

public interface GetTenantRetentionSettingsUseCase {

    TenantRetentionSettings get(String tenantId, AccessContext.User actor);
}
