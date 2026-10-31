package be.myrmacta.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierType;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.out.DossierRepository;
import be.myrmacta.domain.dossier.port.out.DossierTypeRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.shared.PageQuery;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.workflow.model.MyWorkflowTask;
import be.myrmacta.domain.workflow.port.out.WorkflowEnginePort;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowTaskInboxPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListMyTasksUseCaseImplTest {

    private static final String TENANT = "tenant_dev";
    private static final String SUB = "user-1";

    @Mock
    private WorkflowTaskInboxPort inboxPort;
    @Mock
    private WorkflowEnginePort workflowEnginePort;
    @Mock
    private DossierWorkflowLinkRepository linkRepository;
    @Mock
    private DossierRepository dossierRepository;
    @Mock
    private DossierTypeRepository typeRepository;
    @Mock
    private UserGroupsUseCase groups;
    @Mock
    private DossierAccessPolicy accessPolicy;

    @Test
    void list_enrichesAssignedTasksAndSkipsMissingDossierId() {
        Instant created = Instant.parse("2026-08-12T10:00:00Z");
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID linkId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        PageQuery query = PageQuery.of(0, 20);
        when(inboxPort.findActiveAssignedTo(eq(SUB), eq(query))).thenReturn(PageResult.of(
                List.of(
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot(
                                "task-ok", "Instruction", SUB, created, "pi-1"),
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot(
                                "task-skip", "Orphan", SUB, created, "pi-2")),
                0, 20, 2));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(workflowEnginePort.findDossierIdVariable("pi-2")).thenReturn(Optional.empty());
        when(linkRepository.findByProcessInstanceId(TENANT, "pi-1")).thenReturn(Optional.of(link(linkId, dossierId)));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(typeRepository.findByCode(TENANT, "SINISTRE")).thenReturn(Optional.empty());

        ListMyTasksUseCaseImpl useCase = new ListMyTasksUseCaseImpl(
                TENANT,
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC));

        PageResult<MyWorkflowTask> result = useCase.listMine(SUB, query);

        assertThat(result.content()).containsExactly(new MyWorkflowTask(
                "task-ok",
                "Instruction",
                SUB,
                created,
                "pi-1",
                dossierId,
                "DOS-1",
                linkId,
                null));
    }

    @Test
    void list_includesRemainingHoursWhenTypeHasSlaTarget() {
        Instant created = Instant.parse("2026-08-12T10:00:00Z");
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID linkId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant now = Instant.parse("2026-08-12T12:00:00Z");
        PageQuery query = PageQuery.of(0, 20);
        when(inboxPort.findActiveAssignedTo(eq(SUB), eq(query))).thenReturn(PageResult.of(
                List.of(new WorkflowTaskInboxPort.ActiveTaskSnapshot("task-ok", "Instruction", SUB, created, "pi-1")),
                0,
                20,
                1));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(linkRepository.findByProcessInstanceId(TENANT, "pi-1")).thenReturn(Optional.of(link(linkId, dossierId)));
        when(dossierRepository.findById(dossierId))
                .thenReturn(Optional.of(dossier(dossierId, Instant.parse("2026-08-12T10:00:00Z"))));
        when(typeRepository.findByCode(TENANT, "SINISTRE"))
                .thenReturn(Optional.of(new DossierType(
                        UUID.randomUUID(),
                        TENANT,
                        "SINISTRE",
                        "Sinistre",
                        "wf-sin",
                        true,
                        false,
                        List.of(),
                        null,
                        false,
                        48,
                        "system",
                        "system")));

        ListMyTasksUseCaseImpl useCase = new ListMyTasksUseCaseImpl(
                TENANT,
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                Clock.fixed(now, ZoneOffset.UTC));

        PageResult<MyWorkflowTask> result = useCase.listMine(SUB, query);

        assertThat(result.content().getFirst().slaRemainingHours()).isEqualTo(46.0);
    }

    @Test
    void list_propagatesFormKeyFromTaskSnapshot() {
        Instant created = Instant.parse("2026-08-12T10:00:00Z");
        UUID dossierId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID linkId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        PageQuery query = PageQuery.of(0, 20);
        when(inboxPort.findActiveAssignedTo(eq(SUB), eq(query))).thenReturn(PageResult.of(
                List.of(new WorkflowTaskInboxPort.ActiveTaskSnapshot(
                        "task-ok", "Instruction", SUB, created, "pi-1", "dossier-fields")),
                0, 20, 1));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(dossierId.toString()));
        when(linkRepository.findByProcessInstanceId(TENANT, "pi-1")).thenReturn(Optional.of(link(linkId, dossierId)));
        when(dossierRepository.findById(dossierId)).thenReturn(Optional.of(dossier(dossierId)));
        when(typeRepository.findByCode(TENANT, "SINISTRE")).thenReturn(Optional.empty());

        ListMyTasksUseCaseImpl useCase = new ListMyTasksUseCaseImpl(
                TENANT,
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                Clock.fixed(Instant.parse("2026-08-12T12:00:00Z"), ZoneOffset.UTC));

        PageResult<MyWorkflowTask> result = useCase.listMine(SUB, query);

        assertThat(result.content().getFirst().formKey()).isEqualTo("dossier-fields");
    }

    /** TODO §113 (D-GROUP-ASSIGN) — « Tâches » montre aussi les tâches libres des groupes du membre, s'il peut modifier le dossier. */
    @Test
    void list_includesTheFreeTasksOfTheUsersGroups_onlyOnDossiersHeCanEdit() {
        Instant created = Instant.parse("2026-10-31T10:00:00Z");
        UUID editable = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID readOnly = UUID.fromString("33333333-3333-3333-3333-333333333333");
        PageQuery query = PageQuery.of(0, 20);
        var actor = AccessContext.user(SUB, Set.of("DOSSIER_MANAGER"));
        when(groups.groupsOf(SUB)).thenReturn(Set.of("COMPTABILITE"));
        when(inboxPort.findActiveForUser(eq(SUB), eq(Set.of("COMPTABILITE")), eq(query))).thenReturn(PageResult.of(
                List.of(
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot("mine", "Instruction", SUB, created, "pi-1"),
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot("free", "Trier le courrier", null, created, "pi-2"),
                        new WorkflowTaskInboxPort.ActiveTaskSnapshot("hidden", "Trier le courrier", null, created, "pi-3")),
                0, 20, 3));
        when(workflowEnginePort.findDossierIdVariable("pi-1")).thenReturn(Optional.of(editable.toString()));
        when(workflowEnginePort.findDossierIdVariable("pi-2")).thenReturn(Optional.of(editable.toString()));
        when(workflowEnginePort.findDossierIdVariable("pi-3")).thenReturn(Optional.of(readOnly.toString()));
        when(linkRepository.findByProcessInstanceId(eq(TENANT), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(link(UUID.randomUUID(), editable)));
        when(dossierRepository.findById(editable)).thenReturn(Optional.of(dossier(editable)));
        Dossier other = dossier(readOnly);
        when(dossierRepository.findById(readOnly)).thenReturn(Optional.of(other));
        when(accessPolicy.canEdit(dossier(editable), actor)).thenReturn(true);
        when(accessPolicy.canEdit(other, actor)).thenReturn(false);

        PageResult<MyWorkflowTask> result = useCase().listMine(actor, query);

        assertThat(result.content()).extracting(MyWorkflowTask::taskId).containsExactly("mine", "free");
        assertThat(result.content().get(1).assigneeId()).isNull();
    }

    @Test
    void list_withoutAnyGroupAsksOnlyForTheAssignedTasks() {
        PageQuery query = PageQuery.of(0, 20);
        when(groups.groupsOf(SUB)).thenReturn(Set.of());
        when(inboxPort.findActiveForUser(eq(SUB), eq(Set.of()), eq(query))).thenReturn(PageResult.of(List.of(), 0, 20, 0));

        assertThat(useCase().listMine(AccessContext.user(SUB, Set.of()), query).content()).isEmpty();
    }

    private ListMyTasksUseCaseImpl useCase() {
        return new ListMyTasksUseCaseImpl(
                TENANT,
                inboxPort,
                workflowEnginePort,
                linkRepository,
                dossierRepository,
                typeRepository,
                Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC),
                groups,
                accessPolicy);
    }

    private static DossierWorkflowLink link(UUID linkId, UUID dossierId) {
        return new DossierWorkflowLink(
                linkId,
                dossierId,
                TENANT,
                "pi-1",
                "instruction-sinistre",
                1,
                "ACTIVE",
                "trigger",
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                null,
                SUB);
    }

    private static Dossier dossier(UUID dossierId) {
        return dossier(dossierId, Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static Dossier dossier(UUID dossierId, Instant createdAt) {
        return new Dossier(
                dossierId,
                TENANT,
                "DOS-1",
                "SINISTRE",
                "Label",
                DossierStatus.EN_COURS,
                Map.of(),
                null,
                0L,
                SUB,
                createdAt,
                SUB,
                createdAt,
                null,
                null,
                null);
    }
}
