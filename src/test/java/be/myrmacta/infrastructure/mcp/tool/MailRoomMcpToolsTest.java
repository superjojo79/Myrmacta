package be.myrmacta.infrastructure.mcp.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.dossier.DossierAccessPolicy;
import be.myrmacta.domain.dossier.model.AccessContext;
import be.myrmacta.domain.dossier.model.AccessDeniedException;
import be.myrmacta.domain.dossier.model.Dossier;
import be.myrmacta.domain.dossier.model.DossierStatus;
import be.myrmacta.domain.dossier.model.DossierWorkflowLink;
import be.myrmacta.domain.dossier.port.in.GetDossierUseCase;
import be.myrmacta.domain.dossier.port.in.ListDossierRelationsUseCase;
import be.myrmacta.domain.dossier.port.out.DossierElementRepository;
import be.myrmacta.domain.dossier.port.out.DossierWorkflowLinkRepository;
import be.myrmacta.domain.mailroom.port.out.MailMessageIndex;
import be.myrmacta.domain.shared.PageResult;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.model.WorkflowTaskView;
import be.myrmacta.domain.workflow.port.in.ClaimWorkflowTaskUseCase;
import be.myrmacta.domain.workflow.port.in.CompleteTaskUseCase;
import be.myrmacta.domain.workflow.port.in.GetWorkflowTasksUseCase;
import be.myrmacta.domain.workflow.port.in.ListMyTasksUseCase;
import be.myrmacta.domain.workflow.port.in.ReassignTaskToGroupUseCase;
import be.myrmacta.infrastructure.config.TenantProperties;
import be.myrmacta.infrastructure.mailroom.MailThreadAttacher;
import be.myrmacta.infrastructure.mcp.McpAccessContextFactory;
import be.myrmacta.infrastructure.mcp.McpActorScope;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** TODO §113 (D-MAILROOM-MCP) — garde-fous des actions : droits, dossier ouvert, tâche sans formulaire, dryRun sans écriture. */
class MailRoomMcpToolsTest {

    private static final String TENANT = "tenant_dev";

    private final GetDossierUseCase getDossier = mock(GetDossierUseCase.class);
    private final ReassignTaskToGroupUseCase reassign = mock(ReassignTaskToGroupUseCase.class);
    private final GetWorkflowTasksUseCase workflowTasks = mock(GetWorkflowTasksUseCase.class);
    private final ClaimWorkflowTaskUseCase claim = mock(ClaimWorkflowTaskUseCase.class);
    private final CompleteTaskUseCase complete = mock(CompleteTaskUseCase.class);
    private final DossierWorkflowLinkRepository links = mock(DossierWorkflowLinkRepository.class);
    private final DossierAccessPolicy policy = mock(DossierAccessPolicy.class);
    private final DossierElementRepository elements = mock(DossierElementRepository.class);
    private final MailThreadAttacher attacher = mock(MailThreadAttacher.class);
    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final AccessContext.User agent = AccessContext.user("agent", Set.of("DOSSIER_MANAGER"));
    private final Dossier mail = dossier("DOS-COURRIER-1", DossierStatus.EN_COURS);
    private final Dossier invoice = dossier("DOS-FACTURE-1", DossierStatus.EN_COURS);
    private final UUID linkId = UUID.randomUUID();
    private MailRoomMcpTools tools;

    @BeforeEach
    void setUp() {
        TransactionTemplate inline = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
        tools = new MailRoomMcpTools(mock(ListMyTasksUseCase.class), getDossier, mock(ListDossierRelationsUseCase.class),
                mock(MailMessageIndex.class), reassign, workflowTasks, claim, complete, links, policy, elements, attacher, groups,
                new TenantProperties(TENANT), new McpAccessContextFactory(), inline, Clock.systemUTC());
        when(groups.groupsOf("agent")).thenReturn(Set.of("COURRIER"));
        when(getDossier.get(eq(mail.id()), any())).thenReturn(mail);
        when(getDossier.get(eq(invoice.id()), any())).thenReturn(invoice);
        when(links.findById(linkId)).thenReturn(Optional.of(new DossierWorkflowLink(linkId, mail.id(), TENANT, "pi-1", "triCourrier", 1,
                "ACTIVE", "t", Instant.EPOCH, null, null, "u")));
    }

    @Test
    void aDryRunAttachChecksTheRightsWithoutWriting_aClosedTargetIsRefused() {
        Map<String, Object> preview = as(() -> tools.attachMailToDossier(mail.id().toString(), invoice.id().toString(), true));

        assertThat(preview).containsEntry("dryRun", true).containsEntry("targetReference", "DOS-FACTURE-1");
        verify(policy).assertCanEdit(mail, agent);
        verify(policy).assertCanEdit(invoice, agent);
        verifyNoInteractions(attacher, elements);

        Dossier closed = dossier("DOS-FACTURE-2", DossierStatus.CLOTURE);
        when(getDossier.get(eq(closed.id()), any())).thenReturn(closed);
        assertThatThrownBy(() -> as(() -> tools.attachMailToDossier(mail.id().toString(), closed.id().toString(), false)))
                .hasMessageContaining("closed");
        assertThatThrownBy(() -> as(() -> tools.attachMailToDossier(mail.id().toString(), mail.id().toString(), false)))
                .hasMessageContaining("itself");
        verifyNoInteractions(attacher);
    }

    @Test
    void attachingWithoutTheRightToEditTheTargetIsRefusedBeforeAnyWrite() {
        doThrow(new AccessDeniedException("no")).when(policy).assertCanEdit(invoice, agent);

        assertThatThrownBy(() -> as(() -> tools.attachMailToDossier(mail.id().toString(), invoice.id().toString(), false)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(attacher, elements);
    }

    @Test
    void aTaskWithAFormStaysInTheUi_andATaskAssignedToSomeoneElseIsNotTaken() {
        tasks(new WorkflowTaskView("t1", "Répondre", null, "q", Instant.EPOCH, "user-question:avis", List.of()),
                new WorkflowTaskView("t2", "Trier le courrier", "bob", "trierCourrier", Instant.EPOCH, null, List.of("COURRIER")));

        assertThatThrownBy(() -> as(() -> tools.completeTask(linkId.toString(), "t1", false))).hasMessageContaining("has a form");
        assertThatThrownBy(() -> as(() -> tools.completeTask(linkId.toString(), "t2", false))).hasMessageContaining("someone else");
        assertThatThrownBy(() -> as(() -> tools.completeTask(linkId.toString(), "t9", false))).hasMessageContaining("not found");
        verifyNoInteractions(claim, complete, elements);
    }

    @Test
    void aFreeTaskWithoutFormIsClaimed_traced_thenCompleted_butNotOnADryRun() {
        tasks(new WorkflowTaskView("t2", "Trier le courrier", null, "trierCourrier", Instant.EPOCH, null, List.of("COURRIER")));

        assertThat(as(() -> tools.completeTask(linkId.toString(), "t2", true))).containsEntry("done", false);
        verifyNoInteractions(claim, complete, elements);

        assertThat(as(() -> tools.completeTask(linkId.toString(), "t2", false))).containsEntry("done", true);
        verify(claim).claim(linkId, "t2", agent);
        verify(elements).save(any());
        verify(complete).complete("t2", mail.id().toString(), Map.of(), agent);
    }

    /** SR-4 — hors de la file d'un de mes groupes, une tâche sans formulaire reste à l'interface. */
    @Test
    void aTaskOutsideMyGroupsIsRefused() {
        tasks(new WorkflowTaskView("t3", "Valider", null, "valider", Instant.EPOCH, null, List.of("DIRECTION")),
                new WorkflowTaskView("t4", "Sans groupe", null, "libre", Instant.EPOCH, null, List.of()));

        assertThatThrownBy(() -> as(() -> tools.completeTask(linkId.toString(), "t3", false))).hasMessageContaining("your user groups");
        assertThatThrownBy(() -> as(() -> tools.completeTask(linkId.toString(), "t4", false))).hasMessageContaining("your user groups");
        verifyNoInteractions(claim, complete, elements);
    }

    /** SR-1 — un courrier déjà lié au dossier : rien n'est refait, pas de note. */
    @Test
    void attachingAnAlreadyLinkedMailDoesNothing() {
        when(attacher.attach(any(), eq(mail), eq(invoice), any(), any())).thenReturn(false);

        assertThat(as(() -> tools.attachMailToDossier(mail.id().toString(), invoice.id().toString(), false)))
                .containsEntry("done", false).containsEntry("alreadyAttached", true);
        verifyNoInteractions(elements);
    }

    @Test
    void aDryRunReassignOnlyChecks() {
        when(reassign.check(linkId, "t2", "JURIDIQUE", agent)).thenReturn("JURIDIQUE");

        assertThat(as(() -> tools.reassignTaskToGroup(linkId.toString(), "t2", "JURIDIQUE", true))).containsEntry("groupCode", "JURIDIQUE");
        verify(reassign, org.mockito.Mockito.never()).reassign(any(), any(), any(), any());

        as(() -> tools.reassignTaskToGroup(linkId.toString(), "t2", "JURIDIQUE", false));
        verify(reassign).reassign(linkId, "t2", "JURIDIQUE", agent);
    }

    private void tasks(WorkflowTaskView... views) {
        when(workflowTasks.getTasks(eq(linkId), any(), any())).thenReturn(new PageResult<>(List.of(views), 0, 100, views.length, 1));
    }

    private <T> T as(java.util.function.Supplier<T> action) {
        return McpActorScope.call(agent, action);
    }

    private static Dossier dossier(String reference, DossierStatus status) {
        return new Dossier(UUID.randomUUID(), TENANT, reference, "COURRIER", reference, status, Map.of(), null, 0L, "u", Instant.EPOCH,
                "u", Instant.EPOCH, null, null, null);
    }
}
