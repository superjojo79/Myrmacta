package com.flowforge.domain.workflow.port.in;

import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import java.util.List;
import java.util.Optional;

public interface GetWorkflowTypeConfigUseCase {

    Optional<WorkflowTypeConfig> getByKey(String workflowKey);

    List<WorkflowTypeConfig> list();
}
