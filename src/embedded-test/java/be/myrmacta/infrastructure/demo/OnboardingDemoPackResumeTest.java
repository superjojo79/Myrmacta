package be.myrmacta.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.application.workflow.ManualStartOrchestrator;
import be.myrmacta.domain.connector.port.in.ManageConnectorInstanceUseCase;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.CreateDossierTypeUseCase;
import be.myrmacta.domain.dossier.port.in.CreateDossierUseCase;
import be.myrmacta.domain.dossier.port.in.UpdateDossierStatusUseCase;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.in.DeployBpmnProcessUseCase;
import be.myrmacta.domain.workflow.port.in.SaveTaskQuestionSchemaUseCase;
import be.myrmacta.domain.workflow.port.in.SaveWorkflowTypeConfigUseCase;
import be.myrmacta.domain.workflow.port.out.TaskQuestionSchemaRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowTypeConfigRepository;
import be.myrmacta.infrastructure.config.TenantProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.job.api.DeadLetterJobQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.json.JsonMapper;

/**
 * TODO §135 (D-DEMO-INSTALL) — un dossier créé sans processus est relancé ; un processus terminé ne l'est pas.
 * Le marqueur d'installation est Sarah, pas Élise qui passe aussi par {@code validationAcces}.
 */
class OnboardingDemoPackResumeTest {

    private static final String TENANT = "tenant_dev";
    private static final String NADIA = "nadia.claes@example.invalid";
    private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final CreateDossierUseCase createDossier = mock(CreateDossierUseCase.class);
    private final ManualStartOrchestrator starter = mock(ManualStartOrchestrator.class);
    private final TaskService tasks = mock(TaskService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final ManagementService management = mock(ManagementService.class);
    private final ProcessEngine engine = mock(ProcessEngine.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final TaskQuery taskQuery = mock(TaskQuery.class);
    private final ProcessInstanceQuery instances = mock(ProcessInstanceQuery.class);
    private final HistoricProcessInstanceQuery history = mock(HistoricProcessInstanceQuery.class);
    private final DeadLetterJobQuery deadLetters = mock(DeadLetterJobQuery.class);
    private final UUID nadiaId = UUID.randomUUID();
    private OnboardingDemoPack pack;

    @BeforeEach
    void setUp() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(tasks.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskTenantId(any())).thenReturn(taskQuery);
        when(taskQuery.processDefinitionKey(any())).thenReturn(taskQuery);
        when(taskQuery.taskDefinitionKey(any())).thenReturn(taskQuery);
        when(taskQuery.processInstanceId(any())).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(1L);
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        when(task.getProcessInstanceId()).thenReturn("pi-new");
        when(taskQuery.singleResult()).thenReturn(task);
        when(runtime.createProcessInstanceQuery()).thenReturn(instances);
        when(instances.processInstanceTenantId(any())).thenReturn(instances);
        when(instances.variableValueEquals(any(), any())).thenReturn(instances);
        when(instances.list()).thenReturn(List.of());
        HistoryService historyService = mock(HistoryService.class);
        when(engine.getHistoryService()).thenReturn(historyService);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(history);
        when(history.processInstanceTenantId(any())).thenReturn(history);
        when(history.variableValueEquals(any(), any())).thenReturn(history);
        when(history.count()).thenReturn(0L);
        when(management.createDeadLetterJobQuery()).thenReturn(deadLetters);
        when(deadLetters.processInstanceId(any())).thenReturn(deadLetters);
        when(deadLetters.count()).thenReturn(1L);
        when(runtime.getVariable(any(), eq("dossierId"))).thenReturn(nadiaId.toString());
        DossierWorkflowLink link = mock(DossierWorkflowLink.class);
        when(link.processInstanceId()).thenReturn("pi-new");
        when(starter.start(any(), any(), any(), any(), any())).thenReturn(new ManualStartOrchestrator.StartResult(link, false));
        when(dossiers.findByDossierTypeCode(TENANT, OnboardingDemoPack.TYPE)).thenReturn(List.of(dossier(nadiaId, NADIA)));
        pack = new OnboardingDemoPack("exemples/onboarding-collaborateur", "fr", JsonMapper.builder().build(),
                new TenantProperties(TENANT), mock(DossierTypeRepository.class), dossiers, mock(CreateDossierTypeUseCase.class),
                mock(SaveTaskQuestionSchemaUseCase.class), mock(TaskQuestionSchemaRepository.class),
                mock(SaveWorkflowTypeConfigUseCase.class), mock(WorkflowTypeConfigRepository.class),
                mock(ManageConnectorInstanceUseCase.class), mock(DeployBpmnProcessUseCase.class), createDossier,
                mock(UpdateDossierStatusUseCase.class), starter, mock(CompleteTaskUseCase.class), mock(DemoFakeApis.class), tasks,
                runtime, management, engine, transactions, new org.springframework.mock.env.MockEnvironment());
    }

    @Test
    void aDossierWhoseProcessNeverStartedIsStartedAgain_withoutASecondDossier() {
        pack.seedIncident();

        verify(starter).start(eq(nadiaId), eq(OnboardingDemoPack.PROCESS), any(), any(), any());
        verify(createDossier, never()).create(any(), any());
    }

    @Test
    void aFinishedProcessIsNotRestarted() {
        when(history.count()).thenReturn(1L);

        assertThatThrownBy(pack::seedIncident).hasMessageContaining("already finished");

        verify(starter, never()).start(any(), any(), any(), any(), any());
        verify(createDossier, never()).create(any(), any());
    }

    @Test
    void theInstallMarkerIsSarahWaitingForIt_notElisePassingTheSameTask() {
        UUID eliseId = UUID.randomUUID();
        Task elise = taskOn("pi-elise");
        when(taskQuery.list()).thenReturn(List.of(elise));
        when(runtime.getVariable("pi-elise", "dossierId")).thenReturn(eliseId.toString());
        when(dossiers.findById(eliseId)).thenReturn(Optional.of(dossier(eliseId, "elise.vdb@example.org")));

        assertThat(pack.installed()).isFalse();

        UUID sarahId = UUID.randomUUID();
        Task sarah = taskOn("pi-sarah");
        when(taskQuery.list()).thenReturn(List.of(elise, sarah));
        when(runtime.getVariable("pi-sarah", "dossierId")).thenReturn(sarahId.toString());
        when(dossiers.findById(sarahId)).thenReturn(Optional.of(dossier(sarahId, "sarah.dubois@example.org")));

        assertThat(pack.installed()).isTrue();
    }

    private static Task taskOn(String instance) {
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn(instance);
        return task;
    }

    private static Dossier dossier(UUID id, String email) {
        return new Dossier(id, TENANT, "DOS-" + id.toString().substring(0, 8), OnboardingDemoPack.TYPE, "Arrivée",
                DossierStatus.OUVERT, Map.of("emailPersonnel", email), null, 0L, "demo", NOW, "demo", NOW, null, null, null);
    }
}
