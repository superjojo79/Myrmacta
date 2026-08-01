package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.port.in.UpdateDossierTypeUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;

public final class UpdateDossierTypeUseCaseImpl implements UpdateDossierTypeUseCase {

    private final DossierTypeRepository repository;

    public UpdateDossierTypeUseCaseImpl(DossierTypeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public DossierType update(UpdateDossierTypeCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        requireTenantAdmin(actor);

        DossierType existing = repository.findByCode(command.tenantId(), command.code())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + command.tenantId() + "/" + command.code()));
        DossierType updated = existing.withUpdate(
                command.label(),
                command.workflowKey(),
                command.active(),
                command.allowUnknownFields(),
                command.fieldDefinitions());
        repository.save(updated);
        return updated;
    }

    private static void requireTenantAdmin(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }
}
