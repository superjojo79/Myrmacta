package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.port.in.ListDossierTypesUseCase;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ListDossierTypesUseCaseImpl implements ListDossierTypesUseCase {

    private final DossierTypeRepository typeRepository;
    private final DossierTypeAclRepository aclRepository;

    public ListDossierTypesUseCaseImpl(
            DossierTypeRepository typeRepository,
            DossierTypeAclRepository aclRepository) {
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
    }

    @Override
    public List<DossierType> list(String tenantId, AccessContext ctx) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ctx, "ctx");

        return switch (ctx) {
            case AccessContext.System ignored -> typeRepository.findAll(tenantId);
            case AccessContext.Service ignored -> throw new AccessDeniedException(
                    "Service context cannot access dossiers");
            case AccessContext.User user -> listForUser(tenantId, user);
        };
    }

    private List<DossierType> listForUser(String tenantId, AccessContext.User user) {
        if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN")) {
            return typeRepository.findAll(tenantId);
        }

        Set<String> visibleCodes = aclRepository.findBySubject(tenantId, user.userId()).stream()
                .filter(entry -> entry.permission().implies(DossierPermission.VIEW))
                .map(DossierTypeAclEntry::dossierTypeCode)
                .collect(Collectors.toSet());
        if (visibleCodes.isEmpty()) {
            return List.of();
        }

        return typeRepository.findAll(tenantId).stream()
                .filter(type -> visibleCodes.contains(type.code()))
                .toList();
    }
}
