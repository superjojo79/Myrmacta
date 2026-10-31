package be.myrmacta.domain.deadline.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.deadline.model.DeadlineCandidate;
import be.myrmacta.domain.deadline.model.DeadlineLevel;
import be.myrmacta.domain.deadline.model.DeadlineSetting;
import be.myrmacta.domain.deadline.port.out.DeadlineAlertClaims;
import be.myrmacta.domain.deadline.port.out.DeadlineCandidates;
import be.myrmacta.domain.deadline.port.out.DeadlineSettings;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.notification.model.NotificationEvent;
import be.myrmacta.domain.notification.port.in.SendNotificationUseCase;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.TaskGroupReassignment;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowParticipantPort;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §115 (D-DEADLINE-ESCALATE) — rappel J-n, escalade le lendemain du terme, une seule fois par palier. */
class EvaluateDeadlinesUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final LocalDate DEADLINE = LocalDate.parse("2026-11-30");

    private final DeadlineSettings settings = mock(DeadlineSettings.class);
    private final DeadlineCandidates candidates = mock(DeadlineCandidates.class);
    private final DeadlineAlertClaims claims = mock(DeadlineAlertClaims.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final WorkflowParticipantPort participants = mock(WorkflowParticipantPort.class);
    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final WorkflowVisualizationPort engine = mock(WorkflowVisualizationPort.class);
    private final WorkflowLinkGroupRepository linkGroups = mock(WorkflowLinkGroupRepository.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final UUID dossierId = UUID.randomUUID();
    private final DossierWorkflowLink link = mock(DossierWorkflowLink.class);
    private final UUID linkId = UUID.randomUUID();
    private final List<NotificationEvent> sent = new ArrayList<>();
    private EvaluateDeadlinesUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new EvaluateDeadlinesUseCaseImpl(settings, candidates, claims, links, participants, notifications, groups, engine,
                linkGroups, elements);
        DeadlineSetting setting = new DeadlineSetting(TENANT, "FACTURE", "echeance", 5, "DIRECTION");
        when(settings.findAll(TENANT)).thenReturn(List.of(setting));
        when(candidates.list(setting)).thenReturn(List.of(new DeadlineCandidate(dossierId, "DOS-FACTURE-1", "FACTURE", "2026-11-30")));
        when(claims.claim(any(), any(), any(), any(), any())).thenReturn(true);
        when(link.id()).thenReturn(linkId);
        when(link.processInstanceId()).thenReturn("pi-1");
        when(links.findByDossierIdOrderBySequence(dossierId)).thenReturn(List.of(link));
        when(participants.findParticipantUserIds(link)).thenReturn(List.of("alice"));
        when(groups.get("DIRECTION")).thenReturn(new UserGroup(TENANT, "DIRECTION", "Direction", null, Set.of("dora")));
        when(groups.membersOf("DIRECTION")).thenReturn(Set.of("dora"));
        org.mockito.Mockito.doAnswer(call -> {
            sent.add(call.getArgument(0));
            return List.of();
        }).when(notifications).send(any(), anyString());
    }

    @Test
    void beforeTheReminderWindowNothingHappens() {
        assertThat(useCase.scan(TENANT, at("2026-11-24"), Runnable::run)).isZero();

        verifyNoInteractions(claims, notifications, elements, engine);
    }

    @Test
    void fromJMinusNTheParticipantsAreRemindedOnce() {
        useCase.scan(TENANT, at("2026-11-25"), Runnable::run);

        verify(claims).claim(eq(TENANT), eq(dossierId), eq(DeadlineLevel.RAPPEL), eq(DEADLINE), any());
        assertThat(sent).singleElement().satisfies(event -> {
            assertThat(event.userId()).isEqualTo("alice");
            assertThat(event.payload()).containsEntry("type", "DOSSIER_DEADLINE_REMINDER").containsEntry("deadline", "2026-11-30");
        });

        sent.clear();
        when(claims.claim(any(), any(), any(), any(), any())).thenReturn(false);
        useCase.scan(TENANT, at("2026-11-26"), Runnable::run);

        assertThat(sent).isEmpty();
        verifyNoInteractions(elements, engine);
    }

    @Test
    void theDayOfTheDeadlineIsStillOnTime() {
        useCase.scan(TENANT, at("2026-11-30"), Runnable::run);

        verify(claims, never()).claim(any(), any(), eq(DeadlineLevel.ESCALADE), any(), any());
        verifyNoInteractions(engine, elements);
    }

    @Test
    void theDayAfterTheOpenTasksGoToTheEscalationGroup_withANoteAndNotifications() {
        when(links.findActiveByDossierId(dossierId)).thenReturn(Optional.of(link));
        List<WorkflowTaskView> tasks = List.of(task("t1", "Traiter la facture"), task("t2", "Valider"));
        when(engine.listActiveTasks(eq("pi-1"), eq(TENANT), any())).thenReturn(new PageResult<>(tasks, 0, 100, 2, 1));
        when(engine.reassignTaskToGroup(any(), any(), any(), any())).thenReturn(new TaskGroupReassignment("x", List.of(), null));

        useCase.scan(TENANT, at("2026-12-01"), Runnable::run);

        verify(claims).claim(eq(TENANT), eq(dossierId), eq(DeadlineLevel.ESCALADE), eq(DEADLINE), any());
        verify(engine).reassignTaskToGroup("pi-1", "t1", "DIRECTION", TENANT);
        verify(engine).reassignTaskToGroup("pi-1", "t2", "DIRECTION", TENANT);
        verify(linkGroups).assign(TENANT, linkId, "DIRECTION");
        assertThat(sent).extracting(NotificationEvent::userId).containsExactlyInAnyOrder("alice", "dora");
        ArgumentCaptor<DossierElement> note = ArgumentCaptor.forClass(DossierElement.class);
        verify(elements).save(note.capture());
        assertThat(note.getValue().label())
                .isEqualTo("Échéance du 2026-11-30 dépassée : 2 tâche(s) réaffectée(s) au groupe DIRECTION (Traiter la facture, Valider)");
        assertThat(note.getValue().addedBy()).isEqualTo("system");
        assertThat(note.getValue().metadata()).containsEntry("event", "DEADLINE_ESCALATED").containsEntry("escalated", true);
    }

    @Test
    void aDeletedEscalationGroupLeavesTheTasksAlone_andSaysSo() {
        when(groups.get("DIRECTION")).thenThrow(new UserGroupNotFoundException("DIRECTION"));
        when(links.findActiveByDossierId(dossierId)).thenReturn(Optional.of(link));

        useCase.scan(TENANT, at("2026-12-01"), Runnable::run);

        verify(engine, never()).reassignTaskToGroup(any(), any(), any(), any());
        verifyNoInteractions(linkGroups);
        ArgumentCaptor<DossierElement> note = ArgumentCaptor.forClass(DossierElement.class);
        verify(elements).save(note.capture());
        assertThat(note.getValue().label()).isEqualTo("Échéance du 2026-11-30 dépassée : groupe d'escalade introuvable (DIRECTION)");
        assertThat(sent).extracting(NotificationEvent::userId).containsExactly("alice");
    }

    @Test
    void aValueThatIsNotADateIsIgnored_andAFailingDossierDoesNotStopTheScan() {
        UUID other = UUID.randomUUID();
        DeadlineSetting setting = new DeadlineSetting(TENANT, "FACTURE", "echeance", 0, null);
        when(settings.findAll(TENANT)).thenReturn(List.of(setting));
        when(candidates.list(setting)).thenReturn(List.of(new DeadlineCandidate(UUID.randomUUID(), "DOS-2", "FACTURE", "bientôt"),
                new DeadlineCandidate(dossierId, "DOS-1", "FACTURE", "2026-11-30"),
                new DeadlineCandidate(other, "DOS-3", "FACTURE", "2026-11-30T10:00:00Z")));
        when(elements.save(any())).thenThrow(new IllegalStateException("db down")).thenReturn(null);

        int failures = useCase.scan(TENANT, at("2026-12-01"), Runnable::run);

        assertThat(failures).isEqualTo(1);
        verify(claims, times(2)).claim(eq(TENANT), any(), eq(DeadlineLevel.ESCALADE), eq(DEADLINE), any());
        verify(claims).claim(eq(TENANT), eq(other), eq(DeadlineLevel.ESCALADE), eq(DEADLINE), any());
    }

    private static Instant at(String day) {
        return Instant.parse(day + "T09:00:00Z");
    }

    private static WorkflowTaskView task(String id, String name) {
        WorkflowTaskView task = mock(WorkflowTaskView.class);
        when(task.taskId()).thenReturn(id);
        when(task.name()).thenReturn(name);
        return task;
    }
}
