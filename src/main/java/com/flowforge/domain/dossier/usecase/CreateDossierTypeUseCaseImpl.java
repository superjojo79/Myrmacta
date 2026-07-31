package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.CreateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;
import java.util.UUID;

public final class CreateDossierTypeUseCaseImpl implements CreateDossierTypeUseCase {

    private final DossierTypeRepository repository;

    public CreateDossierTypeUseCaseImpl(DossierTypeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DossierType create(CreateDossierTypeCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        requireTenantAdmin(actor);

        var type = new DossierType(
                UUID.randomUUID(),
                command.tenantId(),
                command.code(),
                command.label(),
                command.workflowKey(),
                command.active(),
                command.allowUnknownFields(),
                command.fieldDefinitions());
        repository.save(type);
        return type;
    }

    private static void requireTenantAdmin(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }
}
