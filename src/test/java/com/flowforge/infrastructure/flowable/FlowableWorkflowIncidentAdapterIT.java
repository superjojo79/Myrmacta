package com.flowforge.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowforge.domain.workflow.model.IncidentState;
import com.flowforge.domain.workflow.model.WorkflowIncident;
import com.flowforge.support.OidcClientTestConfiguration;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/** TODO §94 — jobs en échec lus et rejoués sur le moteur réel, par tenant. */
@SpringBootTest
@Import({OidcClientTestConfiguration.class, FlowableWorkflowIncidentAdapterIT.FailingDelegateConfig.class})
class FlowableWorkflowIncidentAdapterIT {

    private static final String TENANT = "tenant_dev";
    private static final String OTHER_TENANT = "tenant_autre";
    static final AtomicBoolean FAIL = new AtomicBoolean(true);

    @TestConfiguration
    static class FailingDelegateConfig {
        @Bean("incidentTestDelegate")
        JavaDelegate incidentTestDelegate() {
            return execution -> {
                if (FAIL.get()) {
                    throw new IllegalStateException("Panne simulée " + "x".repeat(400));
                }
            };
        }
    }

    @Autowired
    private FlowableWorkflowIncidentAdapter adapter;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    private String deploymentId;
    private String otherDeploymentId;

    @BeforeEach
    void deploy() {
        FAIL.set(true);
        deploymentId = repositoryService.createDeployment().name("incident-smoke").tenantId(TENANT)
                .addClasspathResource("incidents/incident-smoke.bpmn20.xml")
                .addClasspathResource("incidents/incident-parent.bpmn20.xml").deploy().getId();
        otherDeploymentId = repositoryService.createDeployment().name("incident-smoke-autre").tenantId(OTHER_TENANT)
                .addClasspathResource("incidents/incident-smoke.bpmn20.xml").deploy().getId();
    }

    @AfterEach
    void undeploy() {
        FAIL.set(false);
        repositoryService.deleteDeployment(deploymentId, true);
        repositoryService.deleteDeployment(otherDeploymentId, true);
    }

    /** Attente bornée : l'exécuteur asynchrone acquiert jobs et minuteurs par intervalles (~10 s chacun). */
    private static <T> T await(Supplier<T> probe, Predicate<T> done) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(120));
        T value = probe.get();
        while (!done.test(value) && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
            value = probe.get();
        }
        return value;
    }

    @Test
    void aFailingAsyncStepIsRetryingThenAnIncidentAndARetryLetsTheProcessResume() throws Exception {
        String pi = runtimeService.startProcessInstanceByKeyAndTenantId("incidentSmoke", Map.of(), TENANT).getId();

        List<WorkflowIncident> retrying = await(() -> adapter.listByProcessInstance(pi, TENANT),
                list -> list.stream().anyMatch(i -> i.state() == IncidentState.RETRYING));
        assertThat(retrying).singleElement().satisfies(i -> {
            assertThat(i.activityId()).isEqualTo("isFragile");
            assertThat(i.activityName()).isEqualTo("Étape fragile");
            assertThat(i.retriesLeft()).isPositive();
            assertThat(i.processInstanceId()).isEqualTo(pi);
            assertThat(i.message()).startsWith("Panne simulée").hasSize(WorkflowIncident.MAX_MESSAGE_LENGTH + 1);
            assertThat(i.since()).isNotNull();
        });

        List<WorkflowIncident> dead = await(() -> adapter.listByProcessInstance(pi, TENANT),
                list -> list.stream().anyMatch(i -> i.state() == IncidentState.INCIDENT));
        WorkflowIncident incident = assertThat(dead).singleElement().actual();
        assertThat(incident.state()).isEqualTo(IncidentState.INCIDENT);
        assertThat(incident.retriesLeft()).isZero();
        assertThat(adapter.find(incident.jobId(), TENANT)).contains(incident);
        assertThat(adapter.listByTenant(TENANT, 1000)).extracting(WorkflowIncident::jobId).contains(incident.jobId());

        FAIL.set(false);
        adapter.retry(incident.jobId(), TENANT, 3);

        List<WorkflowIncident> after = await(() -> adapter.listByProcessInstance(pi, TENANT), List::isEmpty);
        assertThat(after).isEmpty();
        assertThat(await(() -> taskService.createTaskQuery().processInstanceId(pi).count(), n -> n == 1)).isEqualTo(1);
    }

    @Test
    void anotherTenantsFailureIsNeverReturned() throws Exception {
        String other = runtimeService.startProcessInstanceByKeyAndTenantId("incidentSmoke", Map.of(), OTHER_TENANT).getId();
        await(() -> adapter.listByProcessInstance(other, OTHER_TENANT), list -> !list.isEmpty());

        assertThat(adapter.listByProcessInstance(other, TENANT)).isEmpty();
        assertThat(adapter.listByTenant(TENANT, 1000)).noneMatch(i -> i.processInstanceId().equals(other));
        String jobId = adapter.listByProcessInstance(other, OTHER_TENANT).getFirst().jobId();
        assertThat(adapter.find(jobId, TENANT)).isEmpty();
    }

    @Test
    void aFailureInACalledSubProcessIsReportedOnTheCallActivityOfTheRootInstance() throws Exception {
        String parent = runtimeService.startProcessInstanceByKeyAndTenantId("incidentParent", Map.of(), TENANT).getId();

        List<WorkflowIncident> dead = await(() -> adapter.listByProcessInstance(parent, TENANT),
                list -> list.stream().anyMatch(i -> i.state() == IncidentState.INCIDENT));
        WorkflowIncident incident = assertThat(dead).singleElement().actual();
        assertThat(incident.processInstanceId()).isEqualTo(parent);
        assertThat(incident.activityId()).isEqualTo("ipCall");
        assertThat(incident.activityName()).contains("Appel du sous-process").contains("Étape fragile");
        assertThat(adapter.find(incident.jobId(), TENANT)).hasValueSatisfying(
                found -> assertThat(found.processInstanceId()).isEqualTo(parent));
        assertThat(adapter.listByTenant(TENANT, 1000)).anySatisfy(i -> {
            assertThat(i.jobId()).isEqualTo(incident.jobId());
            assertThat(i.processInstanceId()).isEqualTo(parent);
        });

        FAIL.set(false);
        adapter.retry(incident.jobId(), TENANT, 3);
        assertThat(await(() -> adapter.listByProcessInstance(parent, TENANT), List::isEmpty)).isEmpty();
    }

    @Test
    void aDeadLetterOfAnotherTenantCannotBeRetried() throws Exception {
        String other = runtimeService.startProcessInstanceByKeyAndTenantId("incidentSmoke", Map.of(), OTHER_TENANT).getId();
        List<WorkflowIncident> dead = await(() -> adapter.listByProcessInstance(other, OTHER_TENANT),
                list -> list.stream().anyMatch(i -> i.state() == IncidentState.INCIDENT));
        String jobId = dead.getFirst().jobId();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adapter.retry(jobId, TENANT, 3))
                .isInstanceOf(com.flowforge.domain.workflow.model.WorkflowIncidentNotFoundException.class);
        assertThat(adapter.find(jobId, OTHER_TENANT)).hasValueSatisfying(
                i -> assertThat(i.state()).isEqualTo(IncidentState.INCIDENT));
    }
}
