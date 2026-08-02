package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import java.util.List;
import java.util.Optional;

public interface WorkflowTypeConfigRepository {

    WorkflowTypeConfig save(WorkflowTypeConfig config);

    Optional<WorkflowTypeConfig> findByWorkflowKey(String tenantId, String workflowKey);

    List<WorkflowTypeConfig> findAll(String tenantId);
}
