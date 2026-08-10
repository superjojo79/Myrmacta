package com.flowforge.domain.workflow.port.out;

import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import java.util.List;

public interface WorkflowVisualizationPort {

    WorkflowDiagramView getDiagram(String processInstanceId, String tenantId);

    List<ActivityHistoryEntry> getHistory(String processInstanceId, String tenantId);

    List<WorkflowTaskView> listActiveTasks(String processInstanceId, String tenantId);

    void assertTaskOnInstance(String processInstanceId, String taskId, String tenantId);

    void reassignTask(String taskId, String assigneeId, String tenantId);
}
