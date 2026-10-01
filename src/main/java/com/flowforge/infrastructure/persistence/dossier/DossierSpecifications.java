package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

final class DossierSpecifications {

    private DossierSpecifications() {}

    static Specification<DossierJpaEntity> forSearch(String tenantId, DossierSearchPort.Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }

            Set<String> typeCodes = filter.dossierTypeCodes();
            if (typeCodes != null) {
                predicates.add(root.get("dossierTypeCode").in(typeCodes));
            }

            String prefix = filter.referencePrefix();
            if (prefix != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("reference")),
                        escapeLikePrefix(prefix.toLowerCase(Locale.ROOT)) + "%",
                        '\\'));
            }

            for (DossierSearchPort.FieldPredicate fp : filter.fieldPredicates()) {
                predicates.add(fieldPredicate(root.get("fields"), cb, fp));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ---- TODO §81 (D2) : champs dynamiques (PostgreSQL jsonb ; fonctions enregistrées par DossierJsonFunctionContributor) ----

    private static final tools.jackson.databind.ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    /**
     * Égalité et appartenance par <b>containment</b> ({@code fields @> {...}}) : seul usage que l'index GIN
     * {@code jsonb_path_ops} sait servir. Intervalles et préfixes : extraction typée, bornée au cadrage (type de dossier).
     * Toutes les valeurs sont des paramètres liés ; le nom du champ vient de la définition validée du type.
     */
    private static Predicate fieldPredicate(
            jakarta.persistence.criteria.Expression<?> fields,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            DossierSearchPort.FieldPredicate fp) {
        return switch (fp.op()) {
            case EQ -> contains(fields, cb, fp.field(), fp.values().get(0));
            case IN -> cb.or(fp.values().stream()
                    .map(v -> contains(fields, cb, fp.field(), v))
                    .toArray(Predicate[]::new));
            case GTE, LTE -> {
                if (fp.kind() == DossierSearchPort.Kind.NUMBER) {
                    var number = cb.function("flow_jsonb_number", java.math.BigDecimal.class, fields, cb.literal(fp.field()));
                    java.math.BigDecimal bound = (java.math.BigDecimal) fp.values().get(0);
                    yield fp.op() == com.flowforge.domain.dossier.model.FieldCondition.Op.GTE
                            ? cb.greaterThanOrEqualTo(number, bound)
                            : cb.lessThanOrEqualTo(number, bound);
                }
                var text = cb.function("flow_jsonb_text", String.class, fields, cb.literal(fp.field()));
                String bound = (String) fp.values().get(0);
                yield fp.op() == com.flowforge.domain.dossier.model.FieldCondition.Op.GTE
                        ? cb.greaterThanOrEqualTo(text, bound)
                        : cb.lessThanOrEqualTo(text, bound);
            }
            case PREFIX -> {
                var text = cb.function("flow_jsonb_text", String.class, fields, cb.literal(fp.field()));
                yield cb.like(cb.lower(text), escapeLikePrefix(((String) fp.values().get(0)).toLowerCase(Locale.ROOT)) + "%", '\\');
            }
        };
    }

    private static Predicate contains(
            jakarta.persistence.criteria.Expression<?> fields,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String name,
            Object value) {
        String json = JSON.writeValueAsString(java.util.Map.of(name, value));
        return cb.isTrue(cb.function("flow_jsonb_contains", Boolean.class, fields, cb.literal(json)));
    }

    /** Escape LIKE metacharacters so user input is a literal prefix. */
    static String escapeLikePrefix(String prefix) {
        return prefix
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** Helper for tests / clarity — status equality. */
    static Specification<DossierJpaEntity> hasStatus(DossierStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
