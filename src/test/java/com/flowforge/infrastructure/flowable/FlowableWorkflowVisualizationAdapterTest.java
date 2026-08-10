package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceNotFoundException;
import com.flowforge.domain.workflow.model.WorkflowTaskNotFoundException;
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
    void reassignTask_callsSetAssigneeWhenTenantMatches() {
        TaskQuery query = mock(TaskQuery.class);
        Task task = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-1")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getTenantId()).thenReturn(TENANT);

        adapter.reassignTask("task-1", "assignee-sub", TENANT);

        verify(taskService).setAssignee("task-1", "assignee-sub");
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
