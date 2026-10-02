package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.DossierListItem;
import com.flowforge.domain.dossier.model.DossierPage;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaDossierSearchAdapter implements DossierSearchPort {

    private final SpringDataDossierRepository repository;
    private final EntityManager entityManager;

    public JpaDossierSearchAdapter(SpringDataDossierRepository repository, EntityManager entityManager) {
        this.repository = repository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public DossierPage search(String tenantId, Filter filter, int page, int size) {
        Specification<DossierJpaEntity> spec = DossierSpecifications.forSearch(tenantId, filter);
        long total = repository.count(spec);
        if (total == 0) {
            return DossierPage.empty(page, size);
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<DossierJpaEntity> root = cq.from(DossierJpaEntity.class);
        cq.multiselect(
                root.get("id"),
                root.get("reference"),
                root.get("label"),
                root.get("dossierTypeCode"),
                root.get("status"),
                root.get("createdAt"),
                root.get("updatedAt"));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(cb.desc(root.get("updatedAt")), cb.asc(root.get("id")));

        TypedQuery<Object[]> query = entityManager.createQuery(cq);
        // Long multiplication first: page/size are validated by SearchDossiersQuery to keep this
        // in-range, but computing in int would silently wrap before that guarantee could help.
        query.setFirstResult(Math.toIntExact((long) page * size));
        query.setMaxResults(size);
        List<DossierListItem> content = query.getResultList().stream()
                .map(JpaDossierSearchAdapter::toItem)
                .toList();
        return DossierPage.of(content, page, size, total);
    }

    private static DossierListItem toItem(Object[] row) {
        return new DossierListItem(
                (UUID) row[0],
                (String) row[1],
                (String) row[2],
                (String) row[3],
                "",
                (DossierStatus) row[4],
                (Instant) row[5],
                (Instant) row[6]);
    }
}
