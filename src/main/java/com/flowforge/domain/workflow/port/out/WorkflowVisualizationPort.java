package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowTaskView;

public interface WorkflowVisualizationPort {

    WorkflowDiagramView getDiagram(String processInstanceId, String tenantId);

    PageResult<ActivityHistoryEntry> getHistory(String processInstanceId, String tenantId, PageQuery query);

    PageResult<WorkflowTaskView> listActiveTasks(String processInstanceId, String tenantId, PageQuery query);

    void assertTaskOnInstance(String processInstanceId, String taskId, String tenantId);

    /** Reassigns after verifying the task belongs to the process instance and tenant. */
    void reassignTask(String processInstanceId, String taskId, String assigneeId, String tenantId);
}
