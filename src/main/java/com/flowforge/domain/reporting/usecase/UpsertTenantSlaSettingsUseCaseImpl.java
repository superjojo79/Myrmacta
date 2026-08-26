package com.flowforge.domain.reporting.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.TenantSlaSettings;
import com.flowforge.domain.reporting.port.in.UpsertTenantSlaSettingsUseCase;
import com.flowforge.domain.reporting.port.out.TenantSlaSettingsRepository;
import java.time.Clock;
import java.util.Objects;

public final class UpsertTenantSlaSettingsUseCaseImpl implements UpsertTenantSlaSettingsUseCase {

    private final TenantSlaSettingsRepository repository;
    private final TenantAdminGuard tenantAdminGuard;
    private final Clock clock;

    public UpsertTenantSlaSettingsUseCaseImpl(
            TenantSlaSettingsRepository repository, TenantAdminGuard tenantAdminGuard, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public TenantSlaSettings upsert(Command command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdmin(actor);
        TenantSlaSettings settings = new TenantSlaSettings(
                command.tenantId(),
                command.complianceTargetPercent(),
                clock.instant(),
                actor.userId());
        return repository.save(settings);
    }
}
