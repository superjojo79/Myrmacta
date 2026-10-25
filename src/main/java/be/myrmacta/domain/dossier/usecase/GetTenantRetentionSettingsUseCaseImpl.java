package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.TenantRetentionSettings;
import be.myrmacta.domain.dossier.model.TenantRetentionSettingsNotFoundException;
import be.myrmacta.domain.dossier.port.in.GetTenantRetentionSettingsUseCase;
import be.myrmacta.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.util.Objects;

public final class GetTenantRetentionSettingsUseCaseImpl implements GetTenantRetentionSettingsUseCase {

    private final TenantRetentionSettingsRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public GetTenantRetentionSettingsUseCaseImpl(
            TenantRetentionSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public TenantRetentionSettings get(String tenantId, AccessContext.User actor) {
        Objects.requireNonNull(tenantId, "tenantId");
        tenantAdminGuard.requireTenantAdmin(actor);
        return repository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new TenantRetentionSettingsNotFoundException(tenantId));
    }
}
