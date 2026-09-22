package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.reporting.model.OperationalCounts;

public interface GetOperationalCountsUseCase {

    OperationalCounts get(String tenantId, AccessContext ctx);
}
