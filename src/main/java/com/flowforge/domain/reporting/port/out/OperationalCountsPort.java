package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.OperationalCounts;

public interface OperationalCountsPort {

    OperationalCounts current(String tenantId);
}
