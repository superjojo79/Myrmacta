package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.TenantAdminGuard;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;
import java.util.UUID;

public final class CreateDossierTypeUseCaseImpl implements CreateDossierTypeUseCase {

    private final DossierTypeRepository repository;
    private final TenantAdminGuard tenantAdminGuard;

    public CreateDossierTypeUseCaseImpl(
            DossierTypeRepository repository, TenantAdminGuard tenantAdminGuard) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantAdminGuard = Objects.requireNonNull(tenantAdminGuard, "tenantAdminGuard");
    }

    @Override
    public DossierType create(CreateDossierTypeCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        tenantAdminGuard.requireTenantAdminOrApiService(actor);

        var type = new DossierType(
                UUID.randomUUID(),
                command.tenantId(),
                command.code(),
                command.label(),
                command.workflowKey(),
                command.active(),
                command.allowUnknownFields(),
                command.fieldDefinitions(),
                command.retentionDays(),
                command.retentionNever(),
                command.slaTargetHours(),
                actor.userId(),
                actor.userId());
        repository.save(type);
        return type;
    }
}
