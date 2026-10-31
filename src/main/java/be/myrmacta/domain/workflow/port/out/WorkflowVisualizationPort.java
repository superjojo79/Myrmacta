package be.myrmacta.domain.workflow.port.out;

import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.ActivityHistoryEntry;
import be.myrmacta.domain.workflow.model.TaskGroupReassignment;
import be.myrmacta.domain.workflow.model.WorkflowDiagramView;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;

public interface WorkflowVisualizationPort {

    WorkflowDiagramView getDiagram(String processInstanceId, String tenantId);

    PageResult<ActivityHistoryEntry> getHistory(String processInstanceId, String tenantId, PageQuery query);

    PageResult<WorkflowTaskView> listActiveTasks(String processInstanceId, String tenantId, PageQuery query);

    void assertTaskOnInstance(String processInstanceId, String taskId, String tenantId);

    /** Reassigns after verifying the task belongs to the process instance and tenant. */
    void reassignTask(String processInstanceId, String taskId, String assigneeId, String tenantId);

    /**
     * Assigne la tâche à {@code userId} si elle est libre ; sans effet si elle lui est déjà
     * assignée. Lève {@link be.myrmacta.domain.workflow.model.WorkflowTaskAlreadyAssignedException}
     * si quelqu'un d'autre l'a prise, y compris dans une course entre deux prises simultanées.
     */
    void claimTask(String processInstanceId, String taskId, String userId, String tenantId);

    /**
     * TODO §114 (D-GROUP-REASSIGN) — retire l'assigné et les groupes candidats de la tâche, puis la rend candidate pour
     * {@code groupCode} seul. Renvoie l'état d'avant, pour la trace.
     */
    TaskGroupReassignment reassignTaskToGroup(String processInstanceId, String taskId, String groupCode, String tenantId);
}
