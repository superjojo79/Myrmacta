package be.myrmacta.domain.dossier.port.out;

import be.myrmacta.domain.dossier.model.TenantRetentionSettings;
import java.util.Optional;

public interface TenantRetentionSettingsRepository {

    Optional<TenantRetentionSettings> findByTenantId(String tenantId);

    TenantRetentionSettings save(TenantRetentionSettings settings);
}
