package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.TenantAdminGuard;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.TenantRetentionSettings;
import be.myrmacta.domain.dossier.port.in.UpsertTenantRetentionSettingsUseCase;
import be.myrmacta.domain.dossier.port.out.TenantRetentionSettingsRepository;
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
