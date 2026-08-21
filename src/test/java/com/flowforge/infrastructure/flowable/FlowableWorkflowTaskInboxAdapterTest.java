package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.workflow.port.out.WorkflowTaskInboxPort.ActiveTaskSnapshot;
import java.util.Date;
import java.util.List;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowableWorkflowTaskInboxAdapterTest {

    private static final String ASSIGNEE = "user-1";

    private TaskService taskService;
    private FlowableWorkflowTaskInboxAdapter adapter;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        adapter = new FlowableWorkflowTaskInboxAdapter(taskService);
    }

    @Test
    void findActiveAssignedTo_usesNativeListPageAndCount_notFullList() {
        TaskQuery countQuery = mock(TaskQuery.class);
        when(countQuery.taskAssignee(ASSIGNEE)).thenReturn(countQuery);
        when(countQuery.active()).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(137L);
        TaskQuery listQuery = freshListQuery();
        when(taskService.createTaskQuery()).thenReturn(countQuery, listQuery);

        PageQuery query = PageQuery.of(1, 20);
        PageResult<ActiveTaskSnapshot> page = adapter.findActiveAssignedTo(ASSIGNEE, query);

        assertThat(page.totalElements()).isEqualTo(137);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(20);
        verify(countQuery, org.mockito.Mockito.never()).listPage(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void findActiveAssignedTo_mapsTaskFieldsAndDefaultsBlankName() {
        TaskQuery countQuery = mock(TaskQuery.class);
        when(countQuery.taskAssignee(ASSIGNEE)).thenReturn(countQuery);
        when(countQuery.active()).thenReturn(countQuery);
        when(countQuery.count()).thenReturn(1L);

        TaskQuery listQuery = mock(TaskQuery.class);
        when(listQuery.taskAssignee(ASSIGNEE)).thenReturn(listQuery);
        when(listQuery.active()).thenReturn(listQuery);
        when(listQuery.orderByTaskCreateTime()).thenReturn(listQuery);
        when(listQuery.desc()).thenReturn(listQuery);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getName()).thenReturn(null);
        when(task.getAssignee()).thenReturn(ASSIGNEE);
        when(task.getCreateTime()).thenReturn(new Date(0));
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(listQuery.listPage(20, 20)).thenReturn(List.of(task));

        when(taskService.createTaskQuery()).thenReturn(countQuery, listQuery);

        PageResult<ActiveTaskSnapshot> page = adapter.findActiveAssignedTo(ASSIGNEE, PageQuery.of(1, 20));

        assertThat(page.content()).hasSize(1);
        ActiveTaskSnapshot snapshot = page.content().getFirst();
        assertThat(snapshot.taskId()).isEqualTo("task-1");
        assertThat(snapshot.name()).isEmpty();
        assertThat(snapshot.processInstanceId()).isEqualTo("pi-1");
    }

    private TaskQuery freshListQuery() {
        TaskQuery listQuery = mock(TaskQuery.class);
        when(listQuery.taskAssignee(ASSIGNEE)).thenReturn(listQuery);
        when(listQuery.active()).thenReturn(listQuery);
        when(listQuery.orderByTaskCreateTime()).thenReturn(listQuery);
        when(listQuery.desc()).thenReturn(listQuery);
        when(listQuery.listPage(20, 20)).thenReturn(List.of());
        return listQuery;
    }
}
