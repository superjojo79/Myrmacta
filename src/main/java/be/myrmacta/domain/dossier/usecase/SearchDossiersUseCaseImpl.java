package be.myrmacta.domain.dossier.usecase;

import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.DossierListItem;
import be.myrmacta.domain.dossier.model.FieldCondition;
import be.myrmacta.domain.dossier.model.FieldDefinition;
import be.myrmacta.domain.dossier.model.InvalidDossierSearchException;
import be.myrmacta.domain.dossier.model.DossierPage;
import be.myrmacta.domain.dossier.model.DossierPermission;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierTypeAclEntry;
import be.myrmacta.domain.dossier.model.SearchDossiersQuery;
import be.myrmacta.domain.dossier.port.in.SearchDossiersUseCase;
import be.myrmacta.domain.dossier.port.out.DossierSearchPort;
import be.myrmacta.domain.dossier.port.out.DossierTypeAclRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import java.util.EnumSet;
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
            case AccessContext.External ignored -> throw new AccessDeniedException(
                    "External portal context cannot access dossiers");
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
        // Revue D2 M1 : les filtres ne sont validés (donc le schéma du type n'est révélé) qu'APRÈS le contrôle de périmètre —
        // un appelant sans droit sur le type reçoit une page vide, jamais un message qui décrit ses champs.
        DossierPage page = searchPort.search(
                tenantId,
                DossierSearchPort.Filter.restricted(query.status(), restricted, query.referencePrefix())
                        .withFieldPredicates(fieldPredicates(tenantId, query)),
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
        return withLabels(tenantId, searchPort.search(
                tenantId, filter.withFieldPredicates(fieldPredicates(tenantId, query)), query.page(), query.size()));
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

    // ---- TODO §81 (D2) : critères sur champs dynamiques cherchables ----

    private static final java.util.regex.Pattern PLAIN_DECIMAL = java.util.regex.Pattern.compile("-?[0-9]{1,30}(\\.[0-9]{1,15})?");

    private List<DossierSearchPort.FieldPredicate> fieldPredicates(String tenantId, SearchDossiersQuery query) {
        List<FieldCondition> conditions = query.fieldConditions();
        if (conditions.isEmpty()) {
            return List.of();
        }
        // D-RECH-GUARD : un filtre sur champ dynamique exige un cadrage — ici, le type de dossier (le schéma qui fait foi).
        if (query.dossierTypeCode() == null) {
            throw new InvalidDossierSearchException("field filters need a dossierTypeCode: they are resolved against its schema");
        }
        DossierType type = typeRepository.findByCode(tenantId, query.dossierTypeCode())
                .orElseThrow(() -> new InvalidDossierSearchException("unknown dossier type: " + query.dossierTypeCode()));
        List<DossierSearchPort.FieldPredicate> predicates = new java.util.ArrayList<>();
        for (FieldCondition c : conditions) {
            FieldDefinition def = type.fieldDefinitions().stream()
                    .filter(d -> d.name().equals(c.field()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidDossierSearchException(
                            "field '" + c.field() + "' is not defined on type " + type.code()));
            if (!def.isSearchable() || def.isSensitive()) {
                throw new InvalidDossierSearchException("field '" + c.field() + "' is not searchable");
            }
            predicates.add(toPredicate(def, c));
        }
        return predicates;
    }

    private static DossierSearchPort.FieldPredicate toPredicate(FieldDefinition def, FieldCondition c) {
        Set<FieldCondition.Op> allowed = switch (def.type()) {
            case STRING -> EnumSet.of(FieldCondition.Op.EQ, FieldCondition.Op.IN, FieldCondition.Op.PREFIX);
            case SELECT -> EnumSet.of(FieldCondition.Op.EQ, FieldCondition.Op.IN);
            case NUMBER, DATE -> EnumSet.of(FieldCondition.Op.EQ, FieldCondition.Op.GTE, FieldCondition.Op.LTE);
            case BOOLEAN -> EnumSet.of(FieldCondition.Op.EQ);
            default -> throw new InvalidDossierSearchException("field '" + def.name() + "' is not searchable");
        };
        if (!allowed.contains(c.op())) {
            throw new InvalidDossierSearchException("operator " + c.op() + " is not supported on " + def.type() + " field '"
                    + def.name() + "' (allowed: " + allowed + ")");
        }
        DossierSearchPort.Kind kind;
        List<Object> values = new java.util.ArrayList<>();
        switch (def.type()) {
            case NUMBER -> {
                kind = DossierSearchPort.Kind.NUMBER;
                for (String v : c.values()) {
                    String t = v.strip();
                    if (!PLAIN_DECIMAL.matcher(t).matches()) {
                        throw new InvalidDossierSearchException("'" + v + "' is not a plain decimal number (field '" + def.name() + "')");
                    }
                    values.add(new java.math.BigDecimal(t));
                }
            }
            case BOOLEAN -> {
                kind = DossierSearchPort.Kind.BOOLEAN;
                String t = c.values().get(0).strip();
                if (!"true".equals(t) && !"false".equals(t)) {
                    throw new InvalidDossierSearchException("a boolean filter takes true or false (field '" + def.name() + "')");
                }
                values.add(Boolean.valueOf(t));
            }
            case DATE -> {
                kind = DossierSearchPort.Kind.TEXT;
                for (String v : c.values()) {
                    try {
                        values.add(java.time.LocalDate.parse(v.strip()).toString());
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new InvalidDossierSearchException("'" + v + "' is not an ISO date (yyyy-MM-dd) (field '" + def.name() + "')");
                    }
                }
            }
            case SELECT -> {
                kind = DossierSearchPort.Kind.TEXT;
                for (String v : c.values()) {
                    if (!def.options().contains(v)) {
                        throw new InvalidDossierSearchException("'" + v + "' is not an option of field '" + def.name() + "'");
                    }
                    values.add(v);
                }
            }
            default -> {
                kind = DossierSearchPort.Kind.TEXT;
                values.addAll(c.values());
            }
        }
        return new DossierSearchPort.FieldPredicate(def.name(), kind, c.op(), values);
    }
}
