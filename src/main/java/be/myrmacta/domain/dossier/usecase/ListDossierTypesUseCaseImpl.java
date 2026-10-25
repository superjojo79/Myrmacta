package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.port.in.ListDossierTypesUseCase;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
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
    public PageResult<DossierType> list(String tenantId, AccessContext ctx, PageQuery query) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(query, "query");

        return switch (ctx) {
            case AccessContext.System ignored -> typeRepository.page(tenantId, null, query);
            case AccessContext.Service ignored -> throw new AccessDeniedException(
                    "Service context cannot access dossiers");
            case AccessContext.External ignored -> throw new AccessDeniedException(
                    "External portal context cannot access dossiers");
            case AccessContext.User user -> listForUser(tenantId, user, query);
        };
    }

    private PageResult<DossierType> listForUser(String tenantId, AccessContext.User user, PageQuery query) {
        if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN") || user.hasRole("API_SERVICE")) {
            return typeRepository.page(tenantId, null, query);
        }

        Set<String> visibleCodes = aclRepository.findBySubject(tenantId, user.userId()).stream()
                .filter(entry -> entry.permission().implies(DossierPermission.VIEW))
                .map(DossierTypeAclEntry::dossierTypeCode)
                .collect(Collectors.toSet());
        return typeRepository.page(tenantId, visibleCodes, query);
    }
}
