package be.myrmacta.infrastructure.flowable;

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
import org.springframework.stereotype.Component;

/**
 * TODO §114 (D-GROUP-LINK) — une tâche créée sans assigné ni candidat reçoit comme candidat le groupe destinataire de son lien
 * de workflow, s'il en a un. Une tâche qui déclare un assigné ou un {@code candidateGroups} littéral garde sa déclaration.
 */
@Component
class LinkGroupTaskAssigner extends AbstractFlowableEngineEventListener {

    private final RuntimeService runtimeService;
    private final WorkflowLinkGroupRepository linkGroups;

    LinkGroupTaskAssigner(RuntimeService runtimeService, WorkflowLinkGroupRepository linkGroups) {
        super(Set.of(FlowableEngineEventType.TASK_CREATED));
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.linkGroups = Objects.requireNonNull(linkGroups, "linkGroups");
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
        linkIdOf(task).flatMap(linkGroups::findGroup).ifPresent(task::addCandidateGroup);
    }

    /** Un groupe introuvable ou une base indisponible ne doit pas créer une tâche sans destinataire en silence. */
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
