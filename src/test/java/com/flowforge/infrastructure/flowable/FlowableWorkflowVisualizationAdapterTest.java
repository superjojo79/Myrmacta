package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.model.ActivityHistoryEntry;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableWorkflowVisualizationAdapterTest {

    private static final String TENANT = "tenant_dev";
    private static final String PI = "proc-1";
    private static final String DEF = "def-1";

    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private HistoryService historyService;
    private TaskService taskService;
    private FlowableWorkflowVisualizationAdapter adapter;

    @BeforeEach
    void setUp() {
        repositoryService = mock(RepositoryService.class);
        runtimeService = mock(RuntimeService.class);
        historyService = mock(HistoryService.class);
        taskService = mock(TaskService.class);
        adapter = new FlowableWorkflowVisualizationAdapter(
                repositoryService, runtimeService, historyService, taskService);
    }

    @Test
    void getDiagram_returnsNativeXmlAndClassifiesActivityStates() {
        stubActiveProcess();
        when(runtimeService.getActiveActivityIds(PI)).thenReturn(List.of("taskA"));

        HistoricActivityInstance completed = activity("start", null, new Date(), null);
        HistoricActivityInstance failed = activity("svc", null, new Date(), "error: boom");
        HistoricActivityInstance ignored = activity("other", null, new Date(), "cancelled");
        HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
        when(activityQuery.processInstanceId(PI)).thenReturn(activityQuery);
        when(activityQuery.activityTenantId(TENANT)).thenReturn(activityQuery);
        when(activityQuery.list()).thenReturn(List.of(completed, failed, ignored));

        when(repositoryService.getProcessModel(DEF))
                .thenReturn(new ByteArrayInputStream("<xml/>".getBytes(StandardCharsets.UTF_8)));

        WorkflowDiagramView view = adapter.getDiagram(PI, TENANT);

        assertThat(view.bpmnXml()).isEqualTo("<xml/>");
        assertThat(view.activeIds()).containsExactly("taskA");
        assertThat(view.completedIds()).contains("start", "svc", "other");
        assertThat(view.failedIds()).containsExactly("svc");
    }

    @Test
    void getDiagram_wrongTenant_throwsNotFound() {
        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceId(PI)).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceTenantId(TENANT)).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery historicQuery = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.processInstanceId(PI)).thenReturn(historicQuery);
        when(historicQuery.processInstanceTenantId(TENANT)).thenReturn(historicQuery);
        when(historicQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> adapter.getDiagram(PI, TENANT))
                .isInstanceOf(WorkflowProcessInstanceNotFoundException.class);
    }

    @Test
    void getHistory_usesNativeListPageAndCount_notFullList() {
        stubActiveProcess();
        HistoricActivityInstanceQuery countQuery = mock(HistoricActivityInstanceQuery.class);
        when(countQuery.processInstanceId(PI)).thenReturn(countQuery);
        when(countQuery.activityTenantId(TENANT)).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(42L);

        HistoricActivityInstanceQuery listQuery = mock(HistoricActivityInstanceQuery.class);
        when(listQuery.processInstanceId(PI)).thenReturn(listQuery);
        when(listQuery.activityTenantId(TENANT)).thenReturn(listQuery);
        when(listQuery.orderByHistoricActivityInstanceStartTime()).thenReturn(listQuery);
        when(listQuery.asc()).thenReturn(listQuery);
        HistoricActivityInstance historicActivity = activity("start", null, new Date(), null);
        when(listQuery.listPage(20, 20)).thenReturn(List.of(historicActivity));

        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(countQuery, listQuery);

        PageResult<ActivityHistoryEntry> page = adapter.getHistory(PI, TENANT, PageQuery.of(1, 20));

        assertThat(page.totalElements()).isEqualTo(42);
        assertThat(page.content()).hasSize(1);
        verify(countQuery, never()).listPage(any(Integer.class), any(Integer.class));
    }

    @Test
    void listActiveTasks_usesNativeListPageAndCount_notFullList() {
        stubActiveProcess();
        TaskQuery countQuery = mock(TaskQuery.class);
        when(countQuery.processInstanceId(PI)).thenReturn(countQuery);
        when(countQuery.taskTenantId(TENANT)).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(7L);

        TaskQuery listQuery = mock(TaskQuery.class);
        when(listQuery.processInstanceId(PI)).thenReturn(listQuery);
        when(listQuery.taskTenantId(TENANT)).thenReturn(listQuery);
        when(listQuery.orderByTaskCreateTime()).thenReturn(listQuery);
        when(listQuery.asc()).thenReturn(listQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(listQuery.listPage(0, 10)).thenReturn(List.of(task));

        when(taskService.createTaskQuery()).thenReturn(countQuery, listQuery);

        PageResult<WorkflowTaskView> page = adapter.listActiveTasks(PI, TENANT, PageQuery.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.content()).hasSize(1);
        verify(countQuery, never()).listPage(any(Integer.class), any(Integer.class));
        verify(listQuery).orderByTaskCreateTime();
        verify(listQuery).asc();
    }

    @Test
    void listActiveTasks_mapsFormKeyFromTask() {
        stubActiveProcess();
        TaskQuery countQuery = mock(TaskQuery.class);
        when(countQuery.processInstanceId(PI)).thenReturn(countQuery);
        when(countQuery.taskTenantId(TENANT)).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(1L);

        TaskQuery listQuery = mock(TaskQuery.class);
        when(listQuery.processInstanceId(PI)).thenReturn(listQuery);
        when(listQuery.taskTenantId(TENANT)).thenReturn(listQuery);
        when(listQuery.orderByTaskCreateTime()).thenReturn(listQuery);
        when(listQuery.asc()).thenReturn(listQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getFormKey()).thenReturn("document-generation");
        when(listQuery.listPage(0, 10)).thenReturn(List.of(task));

        when(taskService.createTaskQuery()).thenReturn(countQuery, listQuery);

        PageResult<WorkflowTaskView> page = adapter.listActiveTasks(PI, TENANT, PageQuery.of(0, 10));

        assertThat(page.content().getFirst().formKey()).isEqualTo("document-generation");
    }

    @Test
    void reassignTask_callsSetAssigneeWhenTaskBelongsToInstanceAndTenant() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn(PI);
        when(task.getTenantId()).thenReturn(TENANT);

        adapter.reassignTask(PI, "task-1", "assignee-sub", TENANT);

        verify(taskService).setAssignee("task-1", "assignee-sub");
    }

    @Test
    void reassignTask_rejectsTaskFromOtherProcess() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("other-pi");
        when(task.getTenantId()).thenReturn(TENANT);

        assertThatThrownBy(() -> adapter.reassignTask(PI, "task-1", "assignee-sub", TENANT))
                .isInstanceOf(WorkflowTaskNotFoundException.class);
        verify(taskService, never()).setAssignee(any(), any());
    }

    @Test
    void claimTask_claimsTaskBelongingToInstanceAndTenant() {
        stubTaskOnInstance(PI);

        adapter.claimTask(PI, "task-1", "manager-sub", TENANT);

        verify(taskService).claim("task-1", "manager-sub");
    }

    @Test
    void claimTask_translatesAlreadyClaimedIntoDomainConflict() {
        stubTaskOnInstance(PI);
        org.mockito.Mockito.doThrow(new org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException("task-1", "other"))
                .when(taskService).claim("task-1", "manager-sub");

        assertThatThrownBy(() -> adapter.claimTask(PI, "task-1", "manager-sub", TENANT))
                .isInstanceOf(com.flowforge.domain.workflow.model.WorkflowTaskAlreadyAssignedException.class);
    }

    @Test
    void claimTask_rejectsTaskFromOtherProcess() {
        stubTaskOnInstance("other-pi");

        assertThatThrownBy(() -> adapter.claimTask(PI, "task-1", "manager-sub", TENANT))
                .isInstanceOf(WorkflowTaskNotFoundException.class);
        verify(taskService, never()).claim(any(), any());
    }

    private void stubTaskOnInstance(String processInstanceId) {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        when(task.getTenantId()).thenReturn(TENANT);
    }

    @Test
    void assertTaskOnInstance_rejectsTaskFromOtherProcess() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("other-pi");
        when(task.getTenantId()).thenReturn(TENANT);

        assertThatThrownBy(() -> adapter.assertTaskOnInstance(PI, "task-1", TENANT))
                .isInstanceOf(WorkflowTaskNotFoundException.class);
    }

    private void stubActiveProcess() {
        ProcessInstanceQuery runtimeQuery = mock(ProcessInstanceQuery.class);
        ProcessInstance runtime = mock(ProcessInstance.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceId(PI)).thenReturn(runtimeQuery);
        when(runtimeQuery.processInstanceTenantId(TENANT)).thenReturn(runtimeQuery);
        when(runtimeQuery.singleResult()).thenReturn(runtime);
        when(runtime.getProcessDefinitionId()).thenReturn(DEF);
    }

    private static HistoricActivityInstance activity(
            String id, String assignee, Date endTime, String deleteReason) {
        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(activity.getActivityId()).thenReturn(id);
        when(activity.getAssignee()).thenReturn(assignee);
        when(activity.getEndTime()).thenReturn(endTime);
        when(activity.getDeleteReason()).thenReturn(deleteReason);
        when(activity.getStartTime()).thenReturn(new Date(0));
        when(activity.getActivityName()).thenReturn(id);
        when(activity.getDurationInMillis()).thenReturn(1L);
        return activity;
    }
}
