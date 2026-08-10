package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import java.util.UUID;

public interface ReassignWorkflowTaskUseCase {
    void reassign(UUID linkId, String taskId, String assigneeId, AccessContext.User actor);
}
