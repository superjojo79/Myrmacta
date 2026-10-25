package be.myrmacta.infrastructure.persistence.dossier;

import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.reporting.model.DossierSlaRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class JpaSlaReportQueryAdapter {

    private final EntityManager entityManager;

    public JpaSlaReportQueryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public long countDossiers(String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes) {
        if (allowedTypeCodes != null && allowedTypeCodes.isEmpty()) {
            return 0;
        }
        var jpql = new StringBuilder(
                "select count(d) from DossierJpaEntity d where d.tenantId = :tenantId and d.createdAt >= :from and d.createdAt < :to");
        if (allowedTypeCodes != null) {
            jpql.append(" and d.dossierTypeCode in :codes");
        }
        TypedQuery<Long> query = entityManager.createQuery(jpql.toString(), Long.class);
        bind(query, tenantId, from, to, allowedTypeCodes);
        return query.getSingleResult();
    }

    @Transactional(readOnly = true)
    public List<DossierSlaRow> findDossiers(String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes) {
        if (allowedTypeCodes != null && allowedTypeCodes.isEmpty()) {
            return List.of();
        }
        var jpql = new StringBuilder(
                "select d from DossierJpaEntity d where d.tenantId = :tenantId and d.createdAt >= :from and d.createdAt < :to");
        if (allowedTypeCodes != null) {
            jpql.append(" and d.dossierTypeCode in :codes");
        }
        TypedQuery<DossierJpaEntity> query = entityManager.createQuery(jpql.toString(), DossierJpaEntity.class);
        bind(query, tenantId, from, to, allowedTypeCodes);
        return query.getResultList().stream().map(JpaSlaReportQueryAdapter::toRow).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> countProducedDocsByType(Set<UUID> dossierIds) {
        if (dossierIds == null || dossierIds.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = entityManager
                .createQuery(
                        "select d.dossierTypeCode, count(e) from DossierElementJpaEntity e, DossierJpaEntity d "
                                + "where e.dossierId = d.id and e.type = :type and d.id in :ids group by d.dossierTypeCode",
                        Object[].class)
                .setParameter("type", ElementType.PRODUCED_DOC)
                .setParameter("ids", dossierIds)
                .getResultList();
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], (Long) row[1]);
        }
        return Map.copyOf(result);
    }

    private static void bind(
            TypedQuery<?> query, String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes) {
        query.setParameter("tenantId", tenantId);
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (allowedTypeCodes != null) {
            query.setParameter("codes", allowedTypeCodes);
        }
    }

    private static DossierSlaRow toRow(DossierJpaEntity entity) {
        return new DossierSlaRow(
                entity.id(),
                entity.dossierTypeCode(),
                entity.createdAt(),
                entity.closedAt(),
                entity.updatedAt(),
                entity.status());
    }
}
