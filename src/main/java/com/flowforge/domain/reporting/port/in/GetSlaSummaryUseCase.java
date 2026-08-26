package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.SlaSummary;

public interface GetSlaSummaryUseCase {

    SlaSummary get(Query query, AccessContext ctx);

    record Query(String tenantId, String from, String to, String dossierTypeCode, String groupBy) {}
}
