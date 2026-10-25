package be.myrmacta.infrastructure.flowable;

import be.myrmacta.domain.workflow.portal.model.PortalTaskDefinition;
import be.myrmacta.domain.workflow.portal.port.out.PortalTaskLookupPort;
import java.util.Objects;
import java.util.Optional;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

/** TODO §68 — nom et consignes ({@code documentation}) d'une tâche encore ouverte. */
@Component
class FlowablePortalTaskLookupAdapter implements PortalTaskLookupPort {

    private final TaskService taskService;
    private final RepositoryService repositoryService;

    FlowablePortalTaskLookupAdapter(TaskService taskService, RepositoryService repositoryService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService");
        this.repositoryService = Objects.requireNonNull(repositoryService, "repositoryService");
    }

    @Override
    public Optional<OpenTask> findOpenTask(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            return Optional.empty();
        }
        return Optional.of(new OpenTask(task.getId(), task.getProcessInstanceId(), task.getName(), task.getDescription()));
    }

    @Override
    public Optional<PortalTaskDefinition> findExternalTaskDefinition(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return Optional.empty();
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || task.getProcessDefinitionId() == null) {
            return Optional.empty();
        }
        BpmnModel model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        for (org.flowable.bpmn.model.Process process : model.getProcesses()) {
            FlowElement element = process.getFlowElement(task.getTaskDefinitionKey(), true);
            if (element instanceof UserTask userTask) {
                return FlowableBpmnParserAdapter.portalDefinition(userTask);
            }
        }
        return Optional.empty();
    }
}
