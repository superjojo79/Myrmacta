package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.SlaReport;
import java.time.Instant;

public interface GetSlaReportUseCase {

    int MAX_COHORT = 10_000;
    int MAX_PERIOD_DAYS = 366;
    int DEFAULT_PERIOD_DAYS = 30;

    SlaReport get(Query query, AccessContext ctx);

    record Query(String tenantId, Instant from, Instant to, String dossierTypeCode) {}
}
