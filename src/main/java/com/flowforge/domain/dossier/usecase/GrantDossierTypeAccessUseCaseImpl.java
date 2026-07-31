package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.in.GrantDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Objects;

public final class GrantDossierTypeAccessUseCaseImpl implements GrantDossierTypeAccessUseCase {

    private final DossierTypeAclRepository aclRepository;

    public GrantDossierTypeAccessUseCaseImpl(DossierTypeAclRepository aclRepository) {
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public void grant(GrantCommand command, AccessContext.User actor) {
        Objects.requireNonNull(command, "command");
        requireTenantAdmin(actor);

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
