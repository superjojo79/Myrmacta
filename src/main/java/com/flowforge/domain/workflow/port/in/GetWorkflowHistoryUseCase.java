package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import java.util.List;
import java.util.UUID;

public interface GetWorkflowHistoryUseCase {
    List<ActivityHistoryEntry> getHistory(UUID linkId, AccessContext.User actor);
}
