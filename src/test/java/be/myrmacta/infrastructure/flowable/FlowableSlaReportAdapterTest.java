package be.myrmacta.infrastructure.flowable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.reporting.model.ActiveTaskRow;
import be.myrmacta.domain.reporting.model.HistoricStepRow;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FlowableSlaReportAdapterTest {

    private static final String TENANT = "tenant_dev";

    @Mock
    private TaskService taskService;
    @Mock
    private HistoryService historyService;
    @Mock
    private WorkflowEnginePort workflowEnginePort;
    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private DossierWorkflowLinkRepository linkRepository;

    private FlowableSlaReportAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FlowableSlaReportAdapter(
                taskService, historyService, workflowEnginePort, dossierRepository, linkRepository);
    }

    @Test
    void findActiveTasks_mapsAssigneeAndType() {
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Task task = mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("pi-1");
        when(task.getAssignee()).thenReturn("mgr-1");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.active()).thenReturn(query);
        when(query.list()).thenReturn(List.of(task));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId, TENANT, "SIN")));

        List<ActiveTaskRow> rows = adapter.findActiveTasks(TENANT, null);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().assigneeId()).isEqualTo("mgr-1");
        assertThat(rows.getFirst().typeCode()).isEqualTo("SIN");
        assertThat(rows.getFirst().dossierId()).isEqualTo(dossierId);
    }

    @Test
    void findActiveTasks_degradesToEmptyOnFlowableFailure() {
        when(taskService.createTaskQuery()).thenThrow(new RuntimeException("engine down"));

        assertThat(adapter.findActiveTasks(TENANT, null)).isEmpty();
    }

    @Test
    void findCompletedUserTaskDurations_filtersToCohortProcessInstances() {
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(linkRepository.findByDossierIdOrderBySequence(dossierId))
                .thenReturn(List.of(link(dossierId, "pi-1")));
        HistoricActivityInstanceQuery query = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(query);
        when(query.activityType("userTask")).thenReturn(query);
        when(query.finished()).thenReturn(query);
        HistoricActivityInstance matching = mock(HistoricActivityInstance.class);
        when(matching.getProcessInstanceId()).thenReturn("pi-1");
        when(matching.getDurationInMillis()).thenReturn(3_600_000L);
        when(matching.getActivityName()).thenReturn("Instruction");
        HistoricActivityInstance other = mock(HistoricActivityInstance.class);
        when(other.getProcessInstanceId()).thenReturn("pi-other");
        when(query.list()).thenReturn(List.of(matching, other));

        List<HistoricStepRow> rows = adapter.findCompletedUserTaskDurations(Set.of(dossierId));

        assertThat(rows).containsExactly(new HistoricStepRow("Instruction", 3_600_000L));
    }

    @Test
    void findCompletedUserTaskDurations_emptyIdsSkipFlowable() {
        assertThat(adapter.findCompletedUserTaskDurations(Set.of())).isEmpty();
    }

    @Test
    void findCompletedUserTaskDurations_degradesToEmptyOnFailure() {
        UUID dossierId = UUID.randomUUID();
        when(linkRepository.findByDossierIdOrderBySequence(dossierId)).thenThrow(new RuntimeException("db"));

        assertThat(adapter.findCompletedUserTaskDurations(Set.of(dossierId))).isEmpty();
    }

    private static Dossier dossier(UUID id, String tenant, String type) {
        Instant at = Instant.parse("2026-03-01T00:00:00Z");
        return new Dossier(
                id,
                tenant,
                "DOS-1",
                type,
                "Label",
                DossierStatus.EN_COURS,
                Map.of(),
                null,
                0L,
                "admin",
                at,
                "admin",
                at,
                null,
                null,
                null);
    }

    private static DossierWorkflowLink link(UUID dossierId, String processInstanceId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                processInstanceId,
                "instruction-sinistre",
                1,
                "ACTIVE",
                "trigger",
                Instant.parse("2026-03-01T00:00:00Z"),
                null,
                null,
                "admin");
    }
}
