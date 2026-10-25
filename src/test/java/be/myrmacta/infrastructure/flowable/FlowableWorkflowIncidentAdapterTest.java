package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.workflow.model.IncidentNotRetryableException;
import be.myrmacta.domain.workflow.model.IncidentState;
import be.myrmacta.domain.workflow.model.WorkflowIncident;
import java.util.Date;
import java.util.List;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.job.api.Job;
import org.flowable.job.api.JobQuery;
import org.flowable.job.api.TimerJobQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §94 — revue adversariale : job sans instance (M1), rejeu sans effet (L1) ou concurrent (L2), tri stable (L4). */
class FlowableWorkflowIncidentAdapterTest {

    private static final String TENANT = "tenant_dev";

    private final ManagementService management = mock(ManagementService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final DeadLetterJobQuery deadQuery = mock(DeadLetterJobQuery.class, RETURNS_SELF);
    private final JobQuery jobQuery = mock(JobQuery.class, RETURNS_SELF);
    private final TimerJobQuery timerQuery = mock(TimerJobQuery.class, RETURNS_SELF);
    private final ProcessInstanceQuery instanceQuery = mock(ProcessInstanceQuery.class, RETURNS_SELF);
    private FlowableWorkflowIncidentAdapter adapter;

    @BeforeEach
    void setUp() {
        when(management.createDeadLetterJobQuery()).thenReturn(deadQuery);
        when(management.createJobQuery()).thenReturn(jobQuery);
        when(management.createTimerJobQuery()).thenReturn(timerQuery);
        when(runtime.createProcessInstanceQuery()).thenReturn(instanceQuery);
        when(jobQuery.listPage(anyInt(), anyInt())).thenReturn(List.of());
        when(timerQuery.listPage(anyInt(), anyInt())).thenReturn(List.of());
        adapter = new FlowableWorkflowIncidentAdapter(management, runtime, mock(RepositoryService.class));
    }

    private static Job job(String id, String processInstanceId, long createdAt) {
        Job job = mock(Job.class);
        when(job.getId()).thenReturn(id);
        when(job.getProcessInstanceId()).thenReturn(processInstanceId);
        when(job.getElementId()).thenReturn("svc");
        when(job.getElementName()).thenReturn("Étape");
        when(job.getCreateTime()).thenReturn(new Date(createdAt));
        when(job.getExceptionMessage()).thenReturn("boom");
        return job;
    }

    private void rootInstance(String id) {
        ProcessInstance instance = mock(ProcessInstance.class);
        when(instance.getId()).thenReturn(id);
        when(instance.getSuperExecutionId()).thenReturn(null);
        when(instanceQuery.singleResult()).thenReturn(instance);
    }

    @Test
    void aJobWithoutProcessInstanceIsIgnoredInsteadOfBreakingTheTenantList() {
        Job definitionTimer = job("def-timer", null, 2_000);
        Job real = job("j1", "pi-1", 1_000);
        rootInstance("pi-1");
        when(deadQuery.listPage(anyInt(), anyInt())).thenReturn(List.of(definitionTimer, real));

        assertThat(adapter.listByTenant(TENANT, 10)).extracting(WorkflowIncident::jobId).containsExactly("j1");
    }

    @Test
    void findOfAJobWithoutProcessInstanceIsEmpty() {
        Job definitionTimer = job("def-timer", null, 1_000);
        when(deadQuery.singleResult()).thenReturn(definitionTimer);

        assertThat(adapter.find("def-timer", TENANT)).isEmpty();
    }

    @Test
    void equalDatesAreOrderedByJobIdSoPagesAreStable() {
        rootInstance("pi-1");
        Job b = job("b", "pi-1", 1_000);
        Job a = job("a", "pi-1", 1_000);
        when(deadQuery.listPage(anyInt(), anyInt())).thenReturn(List.of(b, a));

        assertThat(adapter.listByTenant(TENANT, 10)).extracting(WorkflowIncident::jobId).containsExactly("a", "b");
        assertThat(adapter.listByTenant(TENANT, 10)).allMatch(i -> i.state() == IncidentState.INCIDENT);
    }

    @Test
    void aRetryThatMovesNothingIsAConflictNotASilentSuccess() {
        when(deadQuery.count()).thenReturn(1L);
        when(management.moveDeadLetterJobToExecutableJob(anyString(), anyInt())).thenReturn(null);

        assertThatThrownBy(() -> adapter.retry("j1", TENANT, 3)).isInstanceOf(IncidentNotRetryableException.class);
    }

    @Test
    void aConcurrentRetryIsAConflictNotAServerError() {
        when(deadQuery.count()).thenReturn(1L);
        when(management.moveDeadLetterJobToExecutableJob(anyString(), anyInt()))
                .thenThrow(new FlowableOptimisticLockingException("already moved"));

        assertThatThrownBy(() -> adapter.retry("j1", TENANT, 3)).isInstanceOf(IncidentNotRetryableException.class)
                .hasMessageContaining("already been retried");
    }
}
