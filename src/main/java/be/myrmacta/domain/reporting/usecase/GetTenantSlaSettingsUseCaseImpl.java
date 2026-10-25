package be.myrmacta.domain.reporting.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.reporting.model.TenantSlaSettings;
import be.myrmacta.domain.reporting.model.TenantSlaSettingsNotFoundException;
import be.myrmacta.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import be.myrmacta.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.util.Objects;

public final class GetTenantSlaSettingsUseCaseImpl implements GetTenantSlaSettingsUseCase {

    private final TenantSlaSettingsRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public GetTenantSlaSettingsUseCaseImpl(
            TenantSlaSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public TenantSlaSettings get(String tenantId, AccessContext.User actor) {
        Objects.requireNonNull(tenantId, "tenantId");
        tenantAdminGuard.requireTenantAdmin(actor);
        return repository
                .findByTenantId(tenantId)
                .orElseThrow(() -> new TenantSlaSettingsNotFoundException(tenantId));
    }
}
