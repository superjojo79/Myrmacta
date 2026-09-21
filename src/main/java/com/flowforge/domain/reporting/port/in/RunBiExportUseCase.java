package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.reporting.UnitOfWork;
import java.time.Instant;

public interface RunBiExportUseCase {

    void run(String tenantId, Instant now, UnitOfWork unitOfWork);
}
