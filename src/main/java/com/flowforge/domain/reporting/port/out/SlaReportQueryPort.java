package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.ActiveTaskRow;
import com.flowforge.domain.reporting.model.DossierSlaRow;
import com.flowforge.domain.reporting.model.HistoricStepRow;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface SlaReportQueryPort {

    long countDossiers(String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes);

    List<DossierSlaRow> findDossiers(String tenantId, Instant from, Instant to, Set<String> allowedTypeCodes);

    Map<String, Long> countProducedDocsByType(Set<UUID> dossierIds);

    List<ActiveTaskRow> findActiveTasks(String tenantId, Set<String> allowedTypeCodes);

    List<HistoricStepRow> findCompletedUserTaskDurations(Set<UUID> dossierIds);
}
