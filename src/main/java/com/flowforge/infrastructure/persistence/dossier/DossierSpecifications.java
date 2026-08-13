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
                        prefix.toLowerCase(Locale.ROOT) + "%"));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Helper for tests / clarity — status equality. */
    static Specification<DossierJpaEntity> hasStatus(DossierStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
