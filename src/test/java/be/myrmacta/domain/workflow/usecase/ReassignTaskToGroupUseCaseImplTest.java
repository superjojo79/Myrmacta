package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierElement;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.model.ElementType;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.TaskGroupReassignment;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import be.myrmacta.domain.workflow.port.out.WorkflowVisualizationPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** TODO §114 (D-GROUP-REASSIGN) — réaffecter une tâche à un autre groupe : éditeur du dossier, groupe connu, trace dans le dossier. */
class ReassignTaskToGroupUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final Instant NOW = Instant.parse("2026-10-31T15:00:00Z");

    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final DossierRepository dossiers = mock(DossierRepository.class);
    private final DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
    private final WorkflowVisualizationPort engine = mock(WorkflowVisualizationPort.class);
    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final WorkflowLinkGroupRepository linkGroups = mock(WorkflowLinkGroupRepository.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final AccessContext.User actor = AccessContext.user("operateur", Set.of("DOSSIER_MANAGER"));
    private final UUID linkId = UUID.randomUUID();
    private final UUID dossierId = UUID.randomUUID();
    private ReassignTaskToGroupUseCaseImpl useCase;
    private Dossier dossier;

    @BeforeEach
    void setUp() {
        useCase = new ReassignTaskToGroupUseCaseImpl(TENANT, links, dossiers, policy, engine, groups, linkGroups, elements,
                Clock.fixed(NOW, ZoneOffset.UTC));
        dossier = new Dossier(dossierId, TENANT, "DOS-COURRIER-1", "COURRIER", "Courrier", DossierStatus.EN_COURS, Map.of(), null, 0L,
                "trigger", NOW, "trigger", NOW, null, null, null);
        when(links.findById(linkId)).thenReturn(Optional.of(new DossierWorkflowLink(linkId, dossierId, TENANT, "pi-1", "triCourrier", 1,
                "A_TRIER", "connector", NOW, null, null, "system:outbox")));
        when(dossiers.findById(dossierId)).thenReturn(Optional.of(dossier));
        when(groups.get("JURIDIQUE")).thenReturn(new UserGroup(TENANT, "JURIDIQUE", "Service juridique", null, Set.of()));
        when(engine.reassignTaskToGroup("pi-1", "task-1", "JURIDIQUE", TENANT))
                .thenReturn(new TaskGroupReassignment("Trier le courrier", List.of("COURRIER"), "alice"));
        when(elements.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void anEditorSendsTheTaskToAnotherGroup_theLinkFollows_andANoteTellsWhoFromWhereToWhere() {
        useCase.reassign(linkId, "task-1", "JURIDIQUE", actor);

        verify(policy).assertCanEdit(dossier, actor);
        verify(engine).reassignTaskToGroup("pi-1", "task-1", "JURIDIQUE", TENANT);
        verify(linkGroups).assign(TENANT, linkId, "JURIDIQUE");
        ArgumentCaptor<DossierElement> note = ArgumentCaptor.forClass(DossierElement.class);
        verify(elements).save(note.capture());
        assertThat(note.getValue().type()).isEqualTo(ElementType.NOTE);
        assertThat(note.getValue().dossierId()).isEqualTo(dossierId);
        assertThat(note.getValue().addedBy()).isEqualTo("operateur");
        assertThat(note.getValue().addedAt()).isEqualTo(NOW);
        assertThat(note.getValue().label()).isEqualTo("Tâche « Trier le courrier » réaffectée de COURRIER (alice) à JURIDIQUE");
        assertThat(note.getValue().metadata())
                .containsEntry("event", "TASK_GROUP_REASSIGNED")
                .containsEntry("taskId", "task-1")
                .containsEntry("fromGroups", List.of("COURRIER"))
                .containsEntry("fromAssignee", "alice")
                .containsEntry("toGroup", "JURIDIQUE");
    }

    @Test
    void someoneWhoCannotEditTheDossierIsRefused_beforeAnyChange() {
        doThrow(new AccessDeniedException("no")).when(policy).assertCanEdit(dossier, actor);

        assertThatThrownBy(() -> useCase.reassign(linkId, "task-1", "JURIDIQUE", actor)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(engine, linkGroups, elements);
    }

    @Test
    void anUnknownGroupIsRefused_beforeAnyChange() {
        when(groups.get("INCONNU")).thenThrow(new UserGroupNotFoundException("INCONNU"));

        assertThatThrownBy(() -> useCase.reassign(linkId, "task-1", "INCONNU", actor)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown user group: INCONNU");
        verifyNoInteractions(engine, linkGroups, elements);
    }

    @Test
    void aTaskWithoutPreviousGroupNorAssigneeSaysSo() {
        when(engine.reassignTaskToGroup("pi-1", "task-1", "JURIDIQUE", TENANT))
                .thenReturn(new TaskGroupReassignment("Traiter", List.of(), null));

        useCase.reassign(linkId, "task-1", "JURIDIQUE", actor);

        ArgumentCaptor<DossierElement> note = ArgumentCaptor.forClass(DossierElement.class);
        verify(elements).save(note.capture());
        assertThat(note.getValue().label()).isEqualTo("Tâche « Traiter » réaffectée de (aucun groupe) à JURIDIQUE");
    }
}
