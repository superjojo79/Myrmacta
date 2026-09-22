package com.flowforge.domain.reporting.port.in;

import com.flowforge.domain.reporting.UnitOfWork;
import java.time.Instant;

public interface EvaluateSlaAlertsUseCase {

    /**
     * @return how many dossiers failed inside their own unit of work. The scan clock still advances.
     */
    int scan(String tenantId, Instant now, UnitOfWork unitOfWork);
}
