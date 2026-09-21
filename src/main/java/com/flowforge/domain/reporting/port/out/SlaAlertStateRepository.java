package com.flowforge.domain.reporting.port.out;

import com.flowforge.domain.reporting.model.SlaAlertState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlaAlertStateRepository {

    /** Deletes every row of the tenant, then inserts {@code states}. */
    void replaceAll(String tenantId, List<SlaAlertState> states);

    Optional<SlaAlertState> find(String tenantId, UUID dossierId);

    SlaAlertState save(SlaAlertState state);
}
