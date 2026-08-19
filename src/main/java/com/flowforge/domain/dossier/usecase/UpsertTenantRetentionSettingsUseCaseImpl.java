package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.TenantRetentionSettings;
import com.flowforge.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import com.flowforge.domain.dossier.port.out.TenantRetentionSettingsRepository;
import java.time.Instant;
import java.util.Objects;

public final class UpsertTenantRetentionSettingsUseCaseImpl
        implements UpsertTenantRetentionSettingsUseCase {

    private final TenantRetentionSettingsRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public UpsertTenantRetentionSettingsUseCaseImpl(
            TenantRetentionSettingsRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public TenantRetentionSettings upsert(UpsertCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdmin(actor);
        TenantRetentionSettings settings = new TenantRetentionSettings(
                command.tenantId(),
                command.defaultRetentionDays(),
                Instant.now(),
                actor.userId());
        return repository.save(settings);
    }
}
