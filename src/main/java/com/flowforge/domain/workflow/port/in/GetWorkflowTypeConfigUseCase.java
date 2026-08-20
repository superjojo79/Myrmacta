package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import java.util.Optional;

public interface GetWorkflowTypeConfigUseCase {

    Optional<WorkflowTypeConfig> getByKey(String workflowKey);

    PageResult<WorkflowTypeConfig> list(PageQuery query);
}
