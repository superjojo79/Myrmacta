package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.MyWorkflowTask;

public interface ListMyTasksUseCase {

    PageResult<MyWorkflowTask> listMine(String assigneeId, PageQuery query);
}
