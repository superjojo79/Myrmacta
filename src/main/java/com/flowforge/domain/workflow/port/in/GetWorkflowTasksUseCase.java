package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import java.util.List;
import java.util.UUID;

public interface GetWorkflowTasksUseCase {
    List<WorkflowTaskView> getTasks(UUID linkId, AccessContext.User actor);
}
