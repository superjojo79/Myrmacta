package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.port.in.RevokeDossierTypeAccessUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import java.util.Objects;

public final class RevokeDossierTypeAccessUseCaseImpl implements RevokeDossierTypeAccessUseCase {

    private final DossierTypeAclRepository aclRepository;

    public RevokeDossierTypeAccessUseCaseImpl(DossierTypeAclRepository aclRepository) {
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public void revoke(String tenantId, String dossierTypeCode, String subjectUserId, AccessContext.User actor) {
        requireTenantAdmin(actor);
        aclRepository.delete(
                Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(dossierTypeCode, "dossierTypeCode"),
                Objects.requireNonNull(subjectUserId, "subjectUserId"));
    }

    private static void requireTenantAdmin(AccessContext.User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.hasRole("TENANT_ADMIN")) {
            throw new AccessDeniedException("TENANT_ADMIN role required");
        }
    }
}
