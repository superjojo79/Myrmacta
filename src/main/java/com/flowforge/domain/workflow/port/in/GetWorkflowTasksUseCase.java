package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import java.util.UUID;

public interface GetWorkflowTasksUseCase {
    PageResult<WorkflowTaskView> getTasks(UUID linkId, AccessContext.User actor, PageQuery query);
}
