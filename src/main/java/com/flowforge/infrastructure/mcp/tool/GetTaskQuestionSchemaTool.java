package com.flowforge.infrastructure.mcp.tool;

import com.flowforge.domain.workflow.model.TaskQuestionSchema;
import com.flowforge.domain.workflow.model.UnknownTaskQuestionSchemaException;
import com.flowforge.domain.workflow.port.in.GetTaskQuestionSchemaUseCase;
import com.flowforge.infrastructure.mcp.McpAccessContextFactory;
import java.util.Objects;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class GetTaskQuestionSchemaTool {

    private final GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase;
    private final McpAccessContextFactory accessContextFactory;

    public GetTaskQuestionSchemaTool(
            GetTaskQuestionSchemaUseCase getTaskQuestionSchemaUseCase, McpAccessContextFactory accessContextFactory) {
        this.getTaskQuestionSchemaUseCase =
                Objects.requireNonNull(getTaskQuestionSchemaUseCase, "getTaskQuestionSchemaUseCase");
        this.accessContextFactory = Objects.requireNonNull(accessContextFactory, "accessContextFactory");
    }

    @McpTool(name = "get_task_question_schema", description = "Get a task question schema by its schemaKey.")
    public TaskQuestionSchema getTaskQuestionSchema(
            @McpToolParam(required = true, description = "Schema key") String schemaKey) {
        return getTaskQuestionSchemaUseCase
                .getByKey(schemaKey, accessContextFactory.currentActor())
                .orElseThrow(() -> new UnknownTaskQuestionSchemaException("Unknown schema: " + schemaKey));
    }
}
