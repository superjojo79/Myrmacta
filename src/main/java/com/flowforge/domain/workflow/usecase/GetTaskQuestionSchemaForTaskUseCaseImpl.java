package com.flowforge.domain.workflow.usecase;

import com.flowforge.domain.workflow.TaskQuestionFormKeys;
import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaForTaskUseCase;
import com.flowforge.domain.workflow.port.out.TaskQuestionSchemaRepository;
import com.flowforge.domain.workflow.port.out.WorkflowEnginePort;
import java.util.Objects;
import java.util.Optional;

public final class GetTaskQuestionSchemaForTaskUseCaseImpl implements GetTaskQuestionSchemaForTaskUseCase {

    private final String tenantId;
    private final TaskQuestionSchemaRepository schemaRepository;
    private final WorkflowEnginePort workflowEnginePort;

    public GetTaskQuestionSchemaForTaskUseCaseImpl(
            String tenantId, TaskQuestionSchemaRepository schemaRepository, WorkflowEnginePort workflowEnginePort) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.schemaRepository = Objects.requireNonNull(schemaRepository, "schemaRepository");
        this.workflowEnginePort = Objects.requireNonNull(workflowEnginePort, "workflowEnginePort");
    }

    @Override
    public TaskQuestionSchema getForTask(String taskId, String schemaKey) {
        String formKey = workflowEnginePort.findFormKeyByTaskId(taskId).orElse(null);
        Optional<String> actualSchemaKey = TaskQuestionFormKeys.schemaKeyOf(formKey);
        if (actualSchemaKey.isEmpty() || !actualSchemaKey.get().equals(schemaKey)) {
            throw new UnknownTaskQuestionSchemaException(
                    "Task " + taskId + " does not reference schema '" + schemaKey + "'");
        }
        return schemaRepository.findByKey(tenantId, schemaKey)
                .orElseThrow(() -> new UnknownTaskQuestionSchemaException("Unknown schema: " + schemaKey));
    }
}
