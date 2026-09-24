package com.flowforge.infrastructure.flowable;

import com.flowforge.domain.workflow.WorkflowVariables;
import com.flowforge.domain.workflow.portal.model.PortalTaskDefinition;
import com.flowforge.domain.workflow.portal.port.in.InvalidatePortalLinksForTaskUseCase;
import com.flowforge.domain.workflow.portal.port.in.IssuePortalLinkUseCase;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.impl.util.ProcessDefinitionUtil;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * TODO §68 D-PORTAL-HOOK — listener Flowable global (patron de {@link FlowableSignalEventListener}) :
 * aucun listener à déclarer dans le BPMN, donc aucune nouvelle surface dans l'allowlist. Présent
 * seulement si le portail est activé.
 *
 * <p>S'exécute dans la transaction du moteur ({@link #isFailOnException()} vrai) : un lien ne peut
 * pas exister pour une tâche dont la création a été annulée (T-ISSUE-8). Les définitions portail
 * sont mises en cache par définition de process (immuable une fois déployée).
 */
@Component
@ConditionalOnProperty(prefix = "app.portal", name = "enabled", havingValue = "true")
public class PortalTaskEventListener extends AbstractFlowableEngineEventListener {

    private final RuntimeService runtimeService;
    private final IssuePortalLinkUseCase issueUseCase;
    private final InvalidatePortalLinksForTaskUseCase invalidateUseCase;
    private final Map<String, Map<String, PortalTaskDefinition>> definitionsByProcessDefinition =
            new ConcurrentHashMap<>();

    public PortalTaskEventListener(
            RuntimeService runtimeService,
            IssuePortalLinkUseCase issueUseCase,
            InvalidatePortalLinksForTaskUseCase invalidateUseCase) {
        super(Set.of(
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_COMPLETED,
                FlowableEngineEventType.ENTITY_DELETED));
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService");
        this.issueUseCase = Objects.requireNonNull(issueUseCase, "issueUseCase");
        this.invalidateUseCase = Objects.requireNonNull(invalidateUseCase, "invalidateUseCase");
    }

    @PostConstruct
    void register() {
        runtimeService.addEventListener(
                this,
                FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.TASK_COMPLETED,
                FlowableEngineEventType.ENTITY_DELETED);
    }

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        if (!(event.getEntity() instanceof TaskEntity task) || task.getProcessDefinitionId() == null) {
            return;
        }
        PortalTaskDefinition definition =
                definitionsFor(task.getProcessDefinitionId()).get(task.getTaskDefinitionKey());
        if (definition == null) {
            return;
        }
        issueUseCase.onExternalTaskCreated(new IssuePortalLinkUseCase.ExternalTaskCreated(
                task.getId(), task.getProcessInstanceId(), dossierIdOf(task).orElse(null), definition));
    }

    @Override
    protected void taskCompleted(FlowableEngineEntityEvent event) {
        if (event.getEntity() instanceof TaskEntity task) {
            invalidateUseCase.onTaskEnded(task.getId());
        }
    }

    @Override
    protected void entityDeleted(FlowableEngineEntityEvent event) {
        if (event.getEntity() instanceof TaskEntity task) {
            invalidateUseCase.onTaskEnded(task.getId());
        }
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    private Map<String, PortalTaskDefinition> definitionsFor(String processDefinitionId) {
        return definitionsByProcessDefinition.computeIfAbsent(processDefinitionId, id -> {
            BpmnModel model = ProcessDefinitionUtil.getBpmnModel(id);
            Map<String, PortalTaskDefinition> byKey = new ConcurrentHashMap<>();
            for (Process process : model.getProcesses()) {
                for (FlowElement element : process.findFlowElementsOfType(UserTask.class, true)) {
                    FlowableBpmnParserAdapter.portalDefinition((UserTask) element)
                            .ifPresent(definition -> byKey.put(definition.taskDefinitionKey(), definition));
                }
            }
            return byKey;
        });
    }

    private static Optional<UUID> dossierIdOf(TaskEntity task) {
        Object raw = task.getVariable(WorkflowVariables.DOSSIER_ID);
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
