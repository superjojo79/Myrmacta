package be.myrmacta.domain.dossier.port.in;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.TenantRetentionSettings;

public interface UpsertTenantRetentionSettingsUseCase {

    TenantRetentionSettings upsert(UpsertCommand command, AccessContext.User actor);

    record UpsertCommand(String tenantId, int defaultRetentionDays) {}
}
