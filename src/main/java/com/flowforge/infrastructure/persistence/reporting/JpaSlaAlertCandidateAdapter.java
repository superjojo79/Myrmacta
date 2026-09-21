package com.flowforge.infrastructure.persistence.reporting;

import com.flowforge.domain.reporting.model.SlaAlertCandidate;
import com.flowforge.domain.reporting.port.out.SlaAlertCandidatePort;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JpaSlaAlertCandidateAdapter implements SlaAlertCandidatePort {

    private final EntityManager entityManager;

    public JpaSlaAlertCandidateAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<SlaAlertCandidate> listCandidates(String tenantId) {
        List<SlaAlertCandidateRow> rows = entityManager
                .createQuery(
                        """
                        select new com.flowforge.infrastructure.persistence.reporting.SlaAlertCandidateRow(
                            d.id, d.reference, d.dossierTypeCode, d.createdAt, d.closedAt, d.updatedAt, d.status, t.slaTargetHours)
                        from DossierJpaEntity d, DossierTypeJpaEntity t
                        where d.tenantId = t.tenantId
                          and d.dossierTypeCode = t.code
                          and d.tenantId = :tenantId
                        """,
                        SlaAlertCandidateRow.class)
                .setParameter("tenantId", tenantId)
                .getResultList();
        return rows.stream()
                .map(row -> new SlaAlertCandidate(
                        row.id(),
                        row.reference(),
                        row.dossierTypeCode(),
                        row.createdAt(),
                        row.closedAt(),
                        row.updatedAt(),
                        row.status().blocksMutation(),
                        row.slaTargetHours()))
                .toList();
    }
}
