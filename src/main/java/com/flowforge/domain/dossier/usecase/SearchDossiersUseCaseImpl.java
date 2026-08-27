package com.flowforge.domain.dossier.usecase;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierListItem;
import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierPermission;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.DossierTypeAclEntry;
import com.flowforge.domain.dossier.model.SearchDossiersQuery;
import com.flowforge.domain.dossier.port.in.SearchDossiersUseCase;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import com.flowforge.domain.dossier.port.out.DossierTypeAclRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SearchDossiersUseCaseImpl implements SearchDossiersUseCase {

    private final DossierSearchPort searchPort;
    private final DossierTypeAclRepository aclRepository;
    private final DossierTypeRepository typeRepository;

    public SearchDossiersUseCaseImpl(
            DossierSearchPort searchPort,
            DossierTypeAclRepository aclRepository,
            DossierTypeRepository typeRepository) {
        this.searchPort = Objects.requireNonNull(searchPort, "searchPort");
        this.aclRepository = Objects.requireNonNull(aclRepository, "aclRepository");
        this.typeRepository = Objects.requireNonNull(typeRepository, "typeRepository");
    }

    @Override
    public DossierPage search(String tenantId, SearchDossiersQuery query, AccessContext ctx) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(ctx, "ctx");

        return switch (ctx) {
            case AccessContext.System ignored -> searchUnrestricted(tenantId, query);
            case AccessContext.Service ignored -> throw new AccessDeniedException(
                    "Service context cannot access dossiers");
            case AccessContext.User user -> searchForUser(tenantId, query, user);
        };
    }

    private DossierPage searchForUser(String tenantId, SearchDossiersQuery query, AccessContext.User user) {
        if (user.hasRole("TENANT_ADMIN") || user.hasRole("WORKFLOW_ADMIN") || user.hasRole("API_SERVICE")) {
            return searchUnrestricted(tenantId, query);
        }

        Set<String> visibleCodes = aclRepository.findBySubject(tenantId, user.userId()).stream()
                .filter(entry -> entry.permission().implies(DossierPermission.VIEW))
                .map(DossierTypeAclEntry::dossierTypeCode)
                .collect(Collectors.toSet());
        if (visibleCodes.isEmpty()) {
            return DossierPage.empty(query.page(), query.size());
        }

        String typeFilter = query.dossierTypeCode();
        if (typeFilter != null && !visibleCodes.contains(typeFilter)) {
            return DossierPage.empty(query.page(), query.size());
        }

        Set<String> restricted = typeFilter == null ? visibleCodes : Set.of(typeFilter);
        DossierPage page = searchPort.search(
                tenantId,
                DossierSearchPort.Filter.restricted(query.status(), restricted, query.referencePrefix()),
                query.page(),
                query.size());
        return withLabels(tenantId, page);
    }

    private DossierPage searchUnrestricted(String tenantId, SearchDossiersQuery query) {
        Set<String> typeCodes = query.dossierTypeCode() == null ? null : Set.of(query.dossierTypeCode());
        DossierSearchPort.Filter filter = typeCodes == null
                ? DossierSearchPort.Filter.unrestricted(query.status(), query.referencePrefix())
                : DossierSearchPort.Filter.restricted(
                        query.status(), typeCodes, query.referencePrefix());
        return withLabels(tenantId, searchPort.search(tenantId, filter, query.page(), query.size()));
    }

    private DossierPage withLabels(String tenantId, DossierPage page) {
        if (page.content().isEmpty()) {
            return page;
        }
        Map<String, String> labels = typeRepository.findAll(tenantId).stream()
                .collect(Collectors.toMap(DossierType::code, DossierType::label, (a, b) -> a));
        List<DossierListItem> labeled = page.content().stream()
                .map(item -> item.withDossierTypeLabel(labels.getOrDefault(item.dossierTypeCode(), "")))
                .toList();
        return new DossierPage(
                labeled, page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
