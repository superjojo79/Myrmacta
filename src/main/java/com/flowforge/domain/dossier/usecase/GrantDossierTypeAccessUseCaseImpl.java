package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierNotFoundException;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.Objects;

public final class GrantDossierTypeAccessUseCaseImpl implements GrantDossierTypeAccessUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierTypeAclRepository aclRepository;

    public GrantDossierTypeAccessUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public void grant(GrantCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        requireTenantAdmin(actor);

        typeRepository.findByCode(command.tenantId(), command.dossierTypeCode())
                .orElseThrow(() -> new DossierNotFoundException(
                        "Dossier type not found: " + command.tenantId() + "/" + command.dossierTypeCode()));

        aclRepository.save(new DossierTypeAclEntry(
                command.tenantId(),
                command.dossierTypeCode(),
                command.subjectUserId(),
                command.permission()));
    }

    private static void requireTenantAdmin(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }
}
