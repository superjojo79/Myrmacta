package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.model.TenantSlaSettingsNotFoundException;
import com.flowforge.domain.reporting.port.in.GetTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
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
