package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.DossierAccessPolicy;
import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierListItem;
import com.flowforge.domain.dossier.model.DossierRelationView;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierType;
import com.flowforge.domain.dossier.model.RelationDirection;
import com.flowforge.domain.dossier.model.RelationType;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.model.FieldDefinition;
import com.flowforge.domain.dossier.model.FieldType;
import com.flowforge.domain.dossier.port.in.ListDossierRelationsUseCase;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierTypeRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.notification.model.NotificationChannel;
import com.flowforge.domain.notification.model.NotificationEvent;
import com.flowforge.domain.notification.port.in.SendNotificationUseCase;
import com.flowforge.domain.shared.PageQuery;
import com.flowforge.domain.shared.PageResult;
import com.flowforge.domain.user.model.KnownUser;
import com.flowforge.domain.user.port.out.KnownUserRepository;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceMissingException;
import com.flowforge.domain.workflow.model.WorkflowTaskView;
import com.flowforge.domain.workflow.port.out.WorkflowParticipantPort;
import com.flowforge.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowLinkVisualizationUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String PROCESS = "smokeProcess";

    private DossierWorkflowLinkRepository linkRepository;
    private DossierRepository dossierRepository;
    private DossierTypeRepository dossierTypeRepository;
    private DossierElementRepository elementRepository;
    private ListDossierRelationsUseCase listDossierRelationsUseCase;
    private WorkflowVisualizationPort visualizationPort;
    private WorkflowParticipantPort participantPort;
    private DossierAccessPolicy dossierAccessPolicy;
    private SendNotificationUseCase sendNotificationUseCase;
    private KnownUserRepository knownUserRepository;
    private WorkflowLinkVisualizationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        dossierRepository = mock(DossierRepository.class);
        dossierTypeRepository = mock(DossierTypeRepository.class);
        elementRepository = mock(DossierElementRepository.class);
        listDossierRelationsUseCase = mock(ListDossierRelationsUseCase.class);
        when(listDossierRelationsUseCase.list(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        visualizationPort = mock(WorkflowVisualizationPort.class);
        participantPort = mock(WorkflowParticipantPort.class);
        dossierAccessPolicy = mock(DossierAccessPolicy.class);
        sendNotificationUseCase = mock(SendNotificationUseCase.class);
        knownUserRepository = mock(KnownUserRepository.class);
        when(dossierTypeRepository.findByCode(anyString(), anyString())).thenReturn(Optional.empty());
        useCase = new WorkflowLinkVisualizationUseCaseImpl(
                TENANT,
                linkRepository,
                dossierRepository,
                dossierTypeRepository,
                elementRepository,
                listDossierRelationsUseCase,
                visualizationPort,
                participantPort,
                dossierAccessPolicy,
                sendNotificationUseCase,
                knownUserRepository);
    }

    @Test
    void getDiagram_allowsParticipantAndDelegatesToVisualizationPort() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(visualizationPort.getDiagram("proc-1", TENANT))
                .thenReturn(new WorkflowDiagramView("<definitions/>", List.of("taskA"), List.of(), List.of()));

        WorkflowDiagramView result = useCase.getDiagram(link.id(), user("starter-sub"));

        assertThat(result.activeIds()).containsExactly("taskA");
        verify(visualizationPort).getDiagram("proc-1", TENANT);
    }

    @Test
    void getDiagram_deniesUserOutsideParticipantsUnlessWorkflowAdmin() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));

        assertThatThrownBy(() -> useCase.getDiagram(link.id(), user("other-sub")))
                .isInstanceOf(AccessDeniedException.class);
        verify(visualizationPort, never()).getDiagram("proc-1", TENANT);
    }

    @Test
    void getDiagram_allowsWorkflowAdminWithoutParticipantLookup() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(visualizationPort.getDiagram("proc-1", TENANT))
                .thenReturn(new WorkflowDiagramView("<definitions/>", List.of(), List.of(), List.of()));

        useCase.getDiagram(link.id(), user("admin-sub", "WORKFLOW_ADMIN"));

        verify(visualizationPort).getDiagram("proc-1", TENANT);
    }

    @Test
    void getDiagram_allowsTenantAdminWithoutParticipantLookup() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(visualizationPort.getDiagram("proc-1", TENANT))
                .thenReturn(new WorkflowDiagramView("<definitions/>", List.of(), List.of(), List.of()));

        useCase.getDiagram(link.id(), user("tenant-admin", "TENANT_ADMIN"));

        verify(visualizationPort).getDiagram("proc-1", TENANT);
        verify(participantPort, never()).findParticipantUserIds(link);
    }

    @Test
    void getDiagram_unknownLinkReturnsNotFound() {
        UUID linkId = UUID.randomUUID();
        when(linkRepository.findById(linkId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.getDiagram(linkId, user("starter-sub")))
                .isInstanceOf(DossierWorkflowLinkNotFoundException.class);
    }

    @Test
    void getDiagram_mapsMissingProcessInstanceToWorkflowConflictException() {
        DossierWorkflowLink link = reservedLink();
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));

        assertThatThrownBy(() -> useCase.getDiagram(link.id(), user("starter-sub")))
                .isInstanceOf(WorkflowProcessInstanceMissingException.class);
    }

    @Test
    void getHistory_allowsParticipantAndDelegatesToVisualizationPortWithPageQuery() {
        DossierWorkflowLink link = activeLink("proc-1");
        com.flowforge.domain.shared.PageQuery query = com.flowforge.domain.shared.PageQuery.of(0, 20);
        var entry = new com.flowforge.domain.workflow.model.ActivityHistoryEntry(
                "start", "Start", null, Instant.parse("2026-08-02T10:00:00Z"), null, null);
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(visualizationPort.getHistory("proc-1", TENANT, query))
                .thenReturn(com.flowforge.domain.shared.PageResult.of(List.of(entry), 0, 20, 1));

        var result = useCase.getHistory(link.id(), user("starter-sub"), query);

        assertThat(result.content()).containsExactly(entry);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getTasks_allowsParticipantAndDelegatesToVisualizationPortWithPageQuery() {
        DossierWorkflowLink link = activeLink("proc-1");
        com.flowforge.domain.shared.PageQuery query = com.flowforge.domain.shared.PageQuery.of(0, 20);
        var task = new com.flowforge.domain.workflow.model.WorkflowTaskView(
                "task-1", "Do it", null, "taskKey", Instant.parse("2026-08-02T10:00:00Z"));
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(visualizationPort.listActiveTasks("proc-1", TENANT, query))
                .thenReturn(com.flowforge.domain.shared.PageResult.of(List.of(task), 0, 20, 1));

        var result = useCase.getTasks(link.id(), user("starter-sub"), query);

        assertThat(result.content()).containsExactly(task);
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void getDossier_allowsReservedLinkAndLoadsDossierPanelDirectlyFromRepositories() {
        DossierWorkflowLink link = reservedLink();
        Dossier dossier = dossier(link.dossierId());
        DossierElement older = element(link.dossierId(), "older", "2026-08-02T10:00:00Z");
        DossierElement newer = element(link.dossierId(), "newer", "2026-08-02T10:05:00Z");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(elementRepository.findByDossierIdOrderByAddedAtAsc(link.dossierId())).thenReturn(List.of(older, newer));

        var result = useCase.getDossier(link.id(), user("starter-sub"));

        assertThat(result.dossier()).isEqualTo(dossier);
        assertThat(result.elements()).containsExactly(older, newer);
        assertThat(result.relations()).isEmpty();
        verify(listDossierRelationsUseCase).list(link.dossierId(), user("starter-sub"));
        verify(dossierAccessPolicy).assertCanView(dossier, user("starter-sub"));
    }

    @Test
    void getDossier_includesRelationsReturnedByListUseCase() {
        DossierWorkflowLink link = reservedLink();
        Dossier dossier = dossier(link.dossierId());
        DossierListItem related = new DossierListItem(
                UUID.randomUUID(),
                "DOS-2",
                "Related",
                "FAC",
                "Facture",
                DossierStatus.OUVERT,
                Instant.parse("2026-08-02T09:00:00Z"),
                Instant.parse("2026-08-02T09:00:00Z"));
        DossierRelationView view = new DossierRelationView(
                UUID.randomUUID(),
                RelationType.LINKED,
                RelationDirection.OUTGOING,
                related,
                "starter-sub",
                Instant.parse("2026-08-15T08:00:00Z"));
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(elementRepository.findByDossierIdOrderByAddedAtAsc(link.dossierId())).thenReturn(List.of());
        when(listDossierRelationsUseCase.list(link.dossierId(), user("starter-sub"))).thenReturn(List.of(view));

        var result = useCase.getDossier(link.id(), user("starter-sub"));

        assertThat(result.relations()).containsExactly(view);
    }

    @Test
    void getDossier_deniesParticipantWithoutDossierTypeViewAcl() {
        DossierWorkflowLink link = reservedLink();
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        org.mockito.Mockito.doThrow(new AccessDeniedException("Access denied for permission VIEW"))
                .when(dossierAccessPolicy)
                .assertCanView(dossier, user("starter-sub"));

        assertThatThrownBy(() -> useCase.getDossier(link.id(), user("starter-sub")))
                .isInstanceOf(AccessDeniedException.class);
        verify(elementRepository, never()).findByDossierIdOrderByAddedAtAsc(link.dossierId());
    }

    @Test
    void getDossier_redactsSensitiveFieldsForNonAdminParticipant() {
        DossierWorkflowLink link = reservedLink();
        Dossier dossier = dossierWithFields(link.dossierId(), Map.of("priority", "HIGH", "ssn", "123-45-6789"));
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(elementRepository.findByDossierIdOrderByAddedAtAsc(link.dossierId())).thenReturn(List.of());
        when(dossierTypeRepository.findByCode(TENANT, "WF")).thenReturn(Optional.of(dossierTypeWithSensitiveSsn()));

        var result = useCase.getDossier(link.id(), user("starter-sub"));

        assertThat(result.dossier().fields()).containsEntry("priority", "HIGH");
        assertThat(result.dossier().fields()).doesNotContainKey("ssn");
    }

    @Test
    void getDossier_showsSensitiveFieldsForTenantAdmin() {
        DossierWorkflowLink link = reservedLink();
        Dossier dossier = dossierWithFields(link.dossierId(), Map.of("priority", "HIGH", "ssn", "123-45-6789"));
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(elementRepository.findByDossierIdOrderByAddedAtAsc(link.dossierId())).thenReturn(List.of());
        when(dossierTypeRepository.findByCode(TENANT, "WF")).thenReturn(Optional.of(dossierTypeWithSensitiveSsn()));

        var result = useCase.getDossier(link.id(), user("tenant-admin", "TENANT_ADMIN"));

        assertThat(result.dossier().fields()).containsEntry("ssn", "123-45-6789");
    }

    @Test
    void reassign_requiresAdminAndChecksTaskBelongsToLinkedInstanceBeforeDelegating() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("assignee-sub"));

        useCase.reassign(link.id(), "task-1", "assignee-sub", user("workflow-admin", "WORKFLOW_ADMIN"));

        verify(visualizationPort).reassignTask("proc-1", "task-1", "assignee-sub", TENANT);
    }

    @Test
    void reassign_notifiesNewAssigneeOnly() {
        // UT-NOTIF-11
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("assignee-sub", "old-assignee"));

        useCase.reassign(link.id(), "task-1", "assignee-sub", user("workflow-admin", "WORKFLOW_ADMIN"));

        org.mockito.ArgumentCaptor<NotificationEvent> captor =
                org.mockito.ArgumentCaptor.forClass(NotificationEvent.class);
        verify(sendNotificationUseCase).send(captor.capture(), org.mockito.ArgumentMatchers.eq(TENANT));
        assertThat(captor.getValue().userId()).isEqualTo("assignee-sub");
        assertThat(captor.getValue().channels()).containsExactly(NotificationChannel.BROWSER);
        assertThat(captor.getValue().payload()).containsEntry("type", "TASK_REASSIGNED");
    }

    @Test
    void reassign_rejectsAssigneeThatIsNotAKnownParticipantOrObserver() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("someone-else"));

        assertThatThrownBy(() -> useCase.reassign(
                        link.id(), "task-1", "assignee-sub", user("admin", "TENANT_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assignee-sub");
        verify(visualizationPort, never()).reassignTask("proc-1", "task-1", "assignee-sub", TENANT);
    }

    @Test
    void reassign_deniesParticipantWithoutAdminRole() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> useCase.reassign(link.id(), "task-1", "assignee-sub", user("starter-sub")))
                .isInstanceOf(AccessDeniedException.class);
        verify(visualizationPort, never()).reassignTask("proc-1", "task-1", "assignee-sub", TENANT);
    }

    @Test
    void reassign_rejectsBlankAssignee() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> useCase.reassign(link.id(), "task-1", " ", user("admin", "TENANT_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reassign_requiresProcessInstance() {
        DossierWorkflowLink link = reservedLink();
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> useCase.reassign(link.id(), "task-1", "assignee", user("admin", "TENANT_ADMIN")))
                .isInstanceOf(WorkflowProcessInstanceMissingException.class);
    }

    @Test
    void reassign_acceptsKnownUserWhoCanEditTheDossierEvenIfNotYetAParticipant() {
        // D-ASSIGN-DIR : l'annuaire local complète la liste des participants comme garde.
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(knownUserRepository.findById(TENANT, "manager-sub"))
                .thenReturn(Optional.of(knownUser("manager-sub", "Manager", "DOSSIER_MANAGER")));
        when(dossierAccessPolicy.canEdit(eq(dossier), any())).thenReturn(true);

        useCase.reassign(link.id(), "task-1", "manager-sub", user("admin", "TENANT_ADMIN"));

        verify(visualizationPort).reassignTask("proc-1", "task-1", "manager-sub", TENANT);
    }

    @Test
    void reassign_rejectsKnownUserWhoCannotEditTheDossier() {
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(knownUserRepository.findById(TENANT, "viewer-sub"))
                .thenReturn(Optional.of(knownUser("viewer-sub", "Viewer", "DOSSIER_VIEWER")));

        assertThatThrownBy(() -> useCase.reassign(link.id(), "task-1", "viewer-sub", user("admin", "TENANT_ADMIN")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(visualizationPort, never()).reassignTask("proc-1", "task-1", "viewer-sub", TENANT);
    }

    @Test
    void listAssignableUsers_returnsKnownUsersWhoCanEditTheDossier() {
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        KnownUser editor = knownUser("editor-sub", "Editor", "DOSSIER_MANAGER");
        KnownUser viewer = knownUser("viewer-sub", "Viewer", "DOSSIER_VIEWER");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(knownUserRepository.findAll(TENANT)).thenReturn(List.of(editor, viewer));
        when(dossierAccessPolicy.canEdit(dossier, AccessContext.user("editor-sub", Set.of("DOSSIER_MANAGER"))))
                .thenReturn(true);

        List<KnownUser> result = useCase.listAssignableUsers(link.id(), user("admin", "WORKFLOW_ADMIN"));

        assertThat(result).containsExactly(editor);
    }

    @Test
    void listAssignableUsers_isReservedToAdmins() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> useCase.listAssignableUsers(link.id(), user("manager-sub", "DOSSIER_MANAGER")))
                .isInstanceOf(AccessDeniedException.class);
        verify(knownUserRepository, never()).findAll(TENANT);
    }

    @Test
    void claim_assignsTaskToActorWhenActorCanEditTheDossier() {
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));

        useCase.claim(link.id(), "task-1", user("manager-sub", "DOSSIER_MANAGER"));

        verify(dossierAccessPolicy).assertCanEdit(eq(dossier), any());
        verify(visualizationPort).claimTask("proc-1", "task-1", "manager-sub", TENANT);
        verify(sendNotificationUseCase, never()).send(any(), anyString());
    }

    @Test
    void claim_deniesActorWithoutEditPermission() {
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        doThrow(new AccessDeniedException("no edit")).when(dossierAccessPolicy).assertCanEdit(eq(dossier), any());

        assertThatThrownBy(() -> useCase.claim(link.id(), "task-1", user("viewer-sub", "DOSSIER_VIEWER")))
                .isInstanceOf(AccessDeniedException.class);
        verify(visualizationPort, never()).claimTask("proc-1", "task-1", "viewer-sub", TENANT);
    }

    @Test
    void claim_rejectsBlankTaskId() {
        assertThatThrownBy(() -> useCase.claim(UUID.randomUUID(), " ", user("manager-sub")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getTasks_allowsDossierEditorWhoIsNotYetAParticipant() {
        // Sans cette ouverture, un éditeur ne verrait jamais la tâche libre qu'il peut prendre.
        DossierWorkflowLink link = activeLink("proc-1");
        Dossier dossier = dossier(link.dossierId());
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));
        when(dossierAccessPolicy.canEdit(eq(dossier), any())).thenReturn(true);
        PageQuery query = PageQuery.of(0, 10);
        when(visualizationPort.listActiveTasks("proc-1", TENANT, query))
                .thenReturn(PageResult.of(
                        List.of(new WorkflowTaskView("task-1", "Instruire", null, "instruire", null)), 0, 10, 1));

        var page = useCase.getTasks(link.id(), user("manager-sub", "DOSSIER_MANAGER"), query);

        assertThat(page.content()).extracting(WorkflowTaskView::taskId).containsExactly("task-1");
    }

    @Test
    void getTasks_deniesNonParticipantWithoutEditPermission() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));
        when(dossierRepository.findById(link.dossierId())).thenReturn(Optional.of(dossier(link.dossierId())));
        when(participantPort.findParticipantUserIds(link)).thenReturn(List.of("starter-sub"));

        assertThatThrownBy(() -> useCase.getTasks(link.id(), user("viewer-sub", "DOSSIER_VIEWER"), PageQuery.of(0, 10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static KnownUser knownUser(String userId, String name, String... roles) {
        return new KnownUser(
                TENANT, userId, name, userId + "@example.org", Set.of(roles), Instant.parse("2026-10-13T08:00:00Z"));
    }

    private static AccessContext.User user(String userId, String... roles) {
        return AccessContext.user(userId, Set.of(roles));
    }

    private static DossierWorkflowLink reservedLink() {
        return link(null);
    }

    private static DossierWorkflowLink activeLink(String processInstanceId) {
        return link(processInstanceId);
    }

    private static DossierWorkflowLink link(String processInstanceId) {
        return new DossierWorkflowLink(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TENANT,
                processInstanceId,
                PROCESS,
                1,
                "STARTED",
                "manual:idem-1",
                Instant.parse("2026-08-02T10:00:00Z"),
                null,
                null,
                "starter-sub");
    }

    private static Dossier dossier(UUID id) {
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "WF",
                "Workflow dossier",
                DossierStatus.OUVERT,
                Map.of("priority", "HIGH"),
                null,
                0,
                "starter-sub",
                Instant.parse("2026-08-02T09:00:00Z"),
                "starter-sub",
                Instant.parse("2026-08-02T09:00:00Z"),
                null,
                null,
                null);
    }

    private static Dossier dossierWithFields(UUID id, Map<String, Object> fields) {
        return new Dossier(
                id,
                TENANT,
                "DOS-1",
                "WF",
                "Workflow dossier",
                DossierStatus.OUVERT,
                fields,
                null,
                0,
                "starter-sub",
                Instant.parse("2026-08-02T09:00:00Z"),
                "starter-sub",
                Instant.parse("2026-08-02T09:00:00Z"),
                null,
                null,
                null);
    }

    private static DossierType dossierTypeWithSensitiveSsn() {
        return DossierType.inherit(
                UUID.randomUUID(),
                TENANT,
                "WF",
                "Workflow dossier type",
                PROCESS,
                true,
                false,
                List.of(
                        new FieldDefinition("priority", "Priority", FieldType.STRING, false, null, List.of(), null, false),
                        new FieldDefinition("ssn", "SSN", FieldType.STRING, false, null, List.of(), null, true)));
    }

    private static DossierElement element(UUID dossierId, String label, String addedAt) {
        return new DossierElement(
                UUID.randomUUID(),
                dossierId,
                TENANT,
                ElementType.PRODUCED_DOC,
                label,
                "tenant_dev/" + label + ".pdf",
                Map.of(),
                "system",
                Instant.parse(addedAt),
                null);
    }
}
