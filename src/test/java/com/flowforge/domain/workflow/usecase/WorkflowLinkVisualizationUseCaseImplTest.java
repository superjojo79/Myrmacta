package com.flowforge.domain.workflow.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowforge.domain.dossier.model.AccessContext;
import com.flowforge.domain.dossier.model.AccessDeniedException;
import com.flowforge.domain.dossier.model.DossierWorkflowLinkNotFoundException;
import com.flowforge.domain.dossier.model.Dossier;
import com.flowforge.domain.dossier.model.DossierElement;
import com.flowforge.domain.dossier.model.DossierStatus;
import com.flowforge.domain.dossier.model.DossierWorkflowLink;
import com.flowforge.domain.dossier.model.ElementType;
import com.flowforge.domain.dossier.port.out.DossierElementRepository;
import com.flowforge.domain.dossier.port.out.DossierRepository;
import com.flowforge.domain.dossier.port.out.DossierWorkflowLinkRepository;
import com.flowforge.domain.workflow.model.WorkflowDiagramView;
import com.flowforge.domain.workflow.model.WorkflowProcessInstanceMissingException;
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
    private DossierElementRepository elementRepository;
    private WorkflowVisualizationPort visualizationPort;
    private WorkflowParticipantPort participantPort;
    private WorkflowLinkVisualizationUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        linkRepository = mock(DossierWorkflowLinkRepository.class);
        dossierRepository = mock(DossierRepository.class);
        elementRepository = mock(DossierElementRepository.class);
        visualizationPort = mock(WorkflowVisualizationPort.class);
        participantPort = mock(WorkflowParticipantPort.class);
        useCase = new WorkflowLinkVisualizationUseCaseImpl(
                TENANT, linkRepository, dossierRepository, elementRepository, visualizationPort, participantPort);
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
    }

    @Test
    void reassign_requiresAdminAndChecksTaskBelongsToLinkedInstanceBeforeDelegating() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        useCase.reassign(link.id(), "task-1", "assignee-sub", user("workflow-admin", "WORKFLOW_ADMIN"));

        verify(visualizationPort).assertTaskOnInstance("proc-1", "task-1", TENANT);
        verify(visualizationPort).reassignTask("task-1", "assignee-sub", TENANT);
    }

    @Test
    void reassign_deniesParticipantWithoutAdminRole() {
        DossierWorkflowLink link = activeLink("proc-1");
        when(linkRepository.findById(link.id())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> useCase.reassign(link.id(), "task-1", "assignee-sub", user("starter-sub")))
                .isInstanceOf(AccessDeniedException.class);
        verify(visualizationPort, never()).reassignTask("task-1", "assignee-sub", TENANT);
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
                null);
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
