package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.user.model.UserGroupNotFoundException;
import be.myrmacta.domain.user.port.in.UserGroupsUseCase;
import be.myrmacta.domain.workflow.WorkflowVariables;
import be.myrmacta.domain.workflow.port.out.WorkflowLinkGroupRepository;
import jakarta.annotation.PostConstruct;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * TODO §114 (D-GROUP-LINK) — une tâche créée sans assigné ni candidat reçoit comme candidat le groupe destinataire de son lien
 * de workflow, s'il en a un. Une tâche qui déclare un assigné ou un {@code candidateGroups} littéral garde sa déclaration.
 */
@Component
class LinkGroupTaskAssigner extends AbstractFlowableEngineEventListener {

    private static final Logger log = LoggerFactory.getLogger(LinkGroupTaskAssigner.class);

    private final RuntimeService runtimeService;
    private final WorkflowLinkGroupRepository linkGroups;
    private final UserGroupsUseCase groups;

    LinkGroupTaskAssigner(RuntimeService runtimeService, WorkflowLinkGroupRepository linkGroups, UserGroupsUseCase groups) {
        super(Set.of(FlowableEngineEventType.TASK_CREATED));
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @PostConstruct
    void register() {
        runtimeService.addEventListener(this, FlowableEngineEventType.TASK_CREATED);
    }

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        if (!(event.getEntity() instanceof TaskEntity task) || task.getAssignee() != null || hasCandidates(task)) {
            return;
        }
        linkIdOf(task).flatMap(linkGroups::findGroup).ifPresent(group -> {
            if (exists(group)) {
                task.addCandidateGroup(group);
            } else {
                // Revue de clôture : un groupe supprimé depuis le routage ne reçoit plus de tâche que personne ne verrait. La
                // tâche reste libre : les éditeurs du dossier la voient et la prennent, ou la réaffectent (§92, D-GROUP-REASSIGN).
                log.warn("Link group {} no longer exists: task {} left without candidate group", group, task.getId());
            }
        });
    }

    private boolean exists(String group) {
        try {
            groups.get(group);
            return true;
        } catch (UserGroupNotFoundException deleted) {
            return false;
        }
    }

    /** Une base indisponible ne doit pas créer une tâche sans destinataire en silence. */
    @Override
    public boolean isFailOnException() {
        return true;
    }

    private static boolean hasCandidates(TaskEntity task) {
        return task.getIdentityLinks().stream().anyMatch(link -> IdentityLinkType.CANDIDATE.equals(link.getType()));
    }

    private static Optional<UUID> linkIdOf(TaskEntity task) {
        Object raw = task.getVariable(WorkflowVariables.DOSSIER_WORKFLOW_LINK_ID);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.toString()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
