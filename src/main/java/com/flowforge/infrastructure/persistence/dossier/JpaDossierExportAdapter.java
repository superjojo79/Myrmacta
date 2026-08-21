package com.flowforge.infrastructure.persistence.dossier;

import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.port.out.DossierExportPort;
import com.flowforge.domain.dossier.port.out.DossierSearchPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.stream.Stream;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

/**
 * Caller must consume (and close) the returned stream within the transaction it was opened in —
 * see {@link DossierExportPort}. Deliberately no {@code @Transactional} here: annotating this
 * method would open and commit its own transaction before the caller ever iterates the stream,
 * invalidating the underlying cursor. The transactional boundary belongs to the caller
 * (DossierExportController, D-CSV design decision).
 */
@Component
public class JpaDossierExportAdapter implements DossierExportPort {

    private final EntityManager entityManager;

    public JpaDossierExportAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public Stream<Dossier> streamForExport(String tenantId, DossierSearchPort.Filter filter) {
        Specification<DossierJpaEntity> spec = DossierSpecifications.forSearch(tenantId, filter);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<DossierJpaEntity> cq = cb.createQuery(DossierJpaEntity.class);
        Root<DossierJpaEntity> root = cq.from(DossierJpaEntity.class);
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        cq.orderBy(cb.asc(root.get("id")));
        return entityManager.createQuery(cq).getResultStream().map(DossierJpaEntity::toDomain);
    }
}
