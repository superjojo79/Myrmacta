package com.flowforge.infrastructure.reporting;

import com.flowforge.domain.reporting.model.ActiveTaskRow;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.HistoricStepRow;
import com.flowforge.domain.reporting.port.out.SlaReportQueryPort;
import com.flowforge.infrastructure.flowable.FlowableSlaReportAdapter;
import com.flowforge.infrastructure.persistence.dossier.JpaSlaReportQueryAdapter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SlaReportQueryFacade implements SlaReportQueryPort {

    private final JpaSlaReportQueryAdapter jpa;
    private final FlowableSlaReportAdapter flowable;

    public SlaReportQueryFacade(JpaSlaReportQueryAdapter jpa, FlowableSlaReportAdapter flowable) {
        this.jpa = jpa;
        this.flowable = flowable;
    }

    @Override
    public long countDossiers(String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes) {
        return jpa.countDossiers(tenantId, from, to, allowedTypeCodes);
    }

    @Override
    public List<DossierSlaRow> findDossiers(
            String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes) {
        return jpa.findDossiers(tenantId, from, to, allowedTypeCodes);
    }

    @Override
    public Map<String, Long> countProducedDocsByType(Set<UUID> dossierIds) {
        return jpa.countProducedDocsByType(dossierIds);
    }

    @Override
    public List<ActiveTaskRow> findActiveTasks(String tenantId, Set<String> allowedTypeCodes) {
        return flowable.findActiveTasks(tenantId, allowedTypeCodes);
    }

    @Override
    public List<HistoricStepRow> findCompletedUserTaskDurations(Set<UUID> dossierIds) {
        return flowable.findCompletedUserTaskDurations(dossierIds);
    }
}
