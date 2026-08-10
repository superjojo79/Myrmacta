package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.workflow.model.StatusOutcome;
import com.flowforge.domain.workflow.model.WorkflowStatusDefinition;
import com.flowforge.domain.workflow.model.WorkflowTypeConfig;
import com.flowforge.domain.workflow.port.out.WorkflowTypeConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableWorkflowParticipantAdapterTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private HistoryService historyService;
    private TaskService taskService;
    private WorkflowTypeConfigRepository configRepository;
    private FlowableWorkflowParticipantAdapter adapter;

    @BeforeEach
    void setUp() {
        historyService = mock(HistoryService.class);
        taskService = mock(TaskService.class);
        configRepository = mock(WorkflowTypeConfigRepository.class);
        adapter = new FlowableWorkflowParticipantAdapter(historyService, taskService, configRepository);
    }

    @Test
    void findParticipantUserIds_withoutProcessInstanceReturnsStartedByAndObserversOnly() {
        DossierWorkflowLink link = link(null, "system:outbox");
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.of(config("observer-a")));

        assertThat(adapter.findParticipantUserIds(link))
                .containsExactly("system:outbox", "observer-a");
        verifyNoInteractions(historyService, taskService);
    }

    @Test
    void findParticipantUserIds_unionsStartedByStarterAssigneesAndObserversDistinctly() {
        DossierWorkflowLink link = link("proc-1", "starter-sub");
        when(configRepository.findByWorkflowKey(TENANT, PROCESS))
                .thenReturn(Optional.of(config("observer-a", "active-assignee")));

        HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance process = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId("proc-1")).thenReturn(processQuery);
        when(processQuery.processInstanceTenantId(TENANT)).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(process);
        when(process.getStartUserId()).thenReturn("flowable-starter");

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task activeTask = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-1")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(TENANT)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(activeTask));
        when(activeTask.getAssignee()).thenReturn("active-assignee");

        HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
        HistoricTaskInstance historicTask = mock(HistoricTaskInstance.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
        when(historicTaskQuery.processInstanceId("proc-1")).thenReturn(historicTaskQuery);
        when(historicTaskQuery.taskTenantId(TENANT)).thenReturn(historicTaskQuery);
        when(historicTaskQuery.list()).thenReturn(List.of(historicTask));
        when(historicTask.getAssignee()).thenReturn("historic-assignee");

        assertThat(adapter.findParticipantUserIds(link))
                .containsExactly(
                        "starter-sub",
                        "flowable-starter",
                        "active-assignee",
                        "historic-assignee",
                        "observer-a");
    }

    @Test
    void findParticipantUserIds_missingConfigYieldsEmptyObserversAndIgnoresBlankIds() {
        DossierWorkflowLink link = link("proc-blank", "  ");
        when(configRepository.findByWorkflowKey(TENANT, PROCESS)).thenReturn(Optional.empty());

        HistoricProcessInstanceQuery processQuery = mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance process = mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(processQuery);
        when(processQuery.processInstanceId("proc-blank")).thenReturn(processQuery);
        when(processQuery.processInstanceTenantId(TENANT)).thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(process);
        when(process.getStartUserId()).thenReturn(null);

        TaskQuery taskQuery = mock(TaskQuery.class);
        Task blankAssignee = mock(Task.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.processInstanceId("proc-blank")).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(TENANT)).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(blankAssignee));
        when(blankAssignee.getAssignee()).thenReturn("");

        HistoricTaskInstanceQuery historicTaskQuery = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicTaskQuery);
        when(historicTaskQuery.processInstanceId("proc-blank")).thenReturn(historicTaskQuery);
        when(historicTaskQuery.taskTenantId(TENANT)).thenReturn(historicTaskQuery);
        when(historicTaskQuery.list()).thenReturn(List.of());

        assertThat(adapter.findParticipantUserIds(link)).isEmpty();
    }

    private static DossierWorkflowLink link(String processInstanceId, String startedBy) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TENANT,
                processInstanceId,
                PROCESS,
                1,
                "STARTED",
                "manual",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                startedBy);
    }

    private static WorkflowTypeConfig config(String... observers) {
        return new WorkflowTypeConfig(
                UUID.randomUUID(),
                TENANT,
                PROCESS,
                "Smoke",
                List.of(new WorkflowStatusDefinition("STARTED", "Started", true, StatusOutcome.NONE)),
                List.of(DossierStatus.OUVERT),
                null,
                List.of(observers));
    }
}
