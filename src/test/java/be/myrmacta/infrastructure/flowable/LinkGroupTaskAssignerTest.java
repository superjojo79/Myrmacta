package be.myrmacta.infrastructure.flowable;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.myrmacta.domain.user.model.UserGroup;
import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.engine.RuntimeService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** TODO §114 (D-GROUP-LINK, revue de clôture de #217) — le groupe du lien devient candidat, sauf s'il a été supprimé depuis. */
class LinkGroupTaskAssignerTest {

    private final WorkflowLinkGroupRepository linkGroups = mock(WorkflowLinkGroupRepository.class);
    private final UserGroupsUseCase groups = mock(UserGroupsUseCase.class);
    private final TaskEntity task = mock(TaskEntity.class);
    private final FlowableEngineEntityEvent event = mock(FlowableEngineEntityEvent.class);
    private final UUID linkId = UUID.randomUUID();
    private LinkGroupTaskAssigner assigner;

    @BeforeEach
    void setUp() {
        assigner = new LinkGroupTaskAssigner(mock(RuntimeService.class), linkGroups, groups);
        when(event.getEntity()).thenReturn(task);
        when(task.getIdentityLinks()).thenReturn(List.of());
        when(task.getVariable("dossierWorkflowLinkId")).thenReturn(linkId.toString());
        when(linkGroups.findGroup(linkId)).thenReturn(Optional.of("COMPTABILITE"));
    }

    @Test
    void theLinkGroupBecomesTheCandidateOfANewFreeTask() {
        when(groups.get("COMPTABILITE")).thenReturn(new UserGroup("tenant_dev", "COMPTABILITE", "Comptabilité", null, Set.of()));

        assigner.taskCreated(event);

        verify(task).addCandidateGroup("COMPTABILITE");
    }

    @Test
    void aGroupDeletedSinceTheRoutingNoLongerReceivesTasks() {
        when(groups.get("COMPTABILITE")).thenThrow(new UserGroupNotFoundException("COMPTABILITE"));

        assigner.taskCreated(event);

        verify(task, never()).addCandidateGroup(any());
    }
}
